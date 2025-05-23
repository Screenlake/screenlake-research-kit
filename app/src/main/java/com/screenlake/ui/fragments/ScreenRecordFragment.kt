package com.screenlake.ui.fragments

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.TransitionDrawable
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.data.database.dao.UserDao
import com.screenlake.databinding.FragmentScreenRecordBinding
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.constants.ConstantSettings.ACTION_PAUSE_SERVICE
import com.screenlake.recorder.constants.ConstantSettings.ACTION_SHOW_RECORDING_FRAGMENT_IMM_RECORD
import com.screenlake.recorder.constants.ConstantSettings.PERMISSIONS_REQUEST_CODE
import com.screenlake.recorder.constants.ConstantSettings.RECORD_TRIGGERED
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.di.DatabaseModule
import com.screenlake.recorder.constants.ConstantSettings.ACTION_STOP_SERVICE
import com.screenlake.recorder.screenshot.ScreenCollector
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.services.ScreenshotService.Companion.appNameVsPackageName
import com.screenlake.recorder.services.ScreenshotService.Companion.restrictedApps
import com.screenlake.recorder.services.TouchAccessibilityService
import com.screenlake.recorder.services.UploadWorker
import com.screenlake.recorder.services.ZipFileWorker
import com.screenlake.recorder.utilities.AssetUtils
import com.screenlake.recorder.utilities.HardwareChecks
import com.screenlake.recorder.utilities.TimeUtility
import com.screenlake.recorder.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

@AndroidEntryPoint
class ScreenRecordFragment : Fragment(R.layout.fragment_screen_record), EasyPermissions.PermissionCallbacks {

    @Inject
    lateinit var logEventDao: LogEventDao

    @Inject
    lateinit var userDao: UserDao

    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    @Inject
    lateinit var screenCollector: ScreenCollector

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentScreenRecordBinding
    private var isRecording = false

    private var projection: MediaProjection? = null
    private lateinit var manager: MediaProjectionManager
    private var handler: Handler? = null
    private val holdDelayMillis = 750L
    private var isTouched = true
    private var isTouchedPause = true
    private val animator: ValueAnimator = ValueAnimator.ofFloat(1f, 1.2f)
    private val animPause = ValueAnimator.ofFloat(1f, 1.2f)
    private val MEDIA_PROJECTION_REQUEST_CODE = 1001

    interface MediaProjectionCallback {
        fun onMediaProjectionRequested()
        fun onMediaProjectionResult(resultCode: Int, data: Intent?)
    }

    private var mediaProjectionCallback: MediaProjectionCallback? = null
    private var restartProjection = false

    private val startMediaProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startScreenshotService(result.resultCode, result.data)
        } else {
            Toast.makeText(requireContext(), "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MediaProjectionCallback) {
            mediaProjectionCallback = context
        } else {
            throw RuntimeException("$context must implement MediaProjectionCallback")
        }
    }

    private fun updateMediaProjectionInService(projection: MediaProjection) {
        ScreenshotService.projection = projection
    }

    private fun startScreenRecordService(projectionData: Intent?) {
        Intent(requireContext(), ScreenshotService::class.java).apply {
            this.action = action
            putExtra("media_projection_data", projectionData)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ContextCompat.startForegroundService(requireContext(), this)
        }
    }

    private fun startServiceRecordingCoroutine() {
        //if the user cancelled screen cast permission projection will be null
        if (projection != null) {
            updateMediaProjectionInService(projection!!)
            ScreenshotService.isRunning.value = true

            lifecycleScope.launch { saveLog(RECORD_TRIGGERED, "true") }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if we should restart projection from arguments
        arguments?.let {
            restartProjection = it.getBoolean("restartProjection", false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_screen_record, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = DataBindingUtil.bind(view)!!
        binding.fragment = this

        // If restarting, request media projection immediately
//        if (restartProjection) {
//            requestScreenCapture()
//        }

        Timber.d("Record Fragment created!")

        lifecycleScope.launch {
            val allApps = withContext(Dispatchers.Default) {
                generalOperationsRepository.getALlApps()
            }

            val restricted = allApps.filter { it.isUserRestricted }.map { it.appName }.toHashSet()

            restrictedApps.postValue(restricted)
        }

        checkConnectionStatus()

        binding.button2.setOnClickListener {
            uploadCommand()
        }

        binding.buttonExplainerText.text = getString(R.string.hold_to_record)

        if (amplifyRepository.email.isBlank()) {
            lifecycleScope.launch {
                val user = withContext(Dispatchers.Default) { userDao.getUser() }
                if (!user.email.isNullOrBlank()) {
                    amplifyRepository.email = user.email ?: ""
                } else {
                    if (user.email == null) {
                        userDao.deleteUser()
                        MainActivity.isLoggedIn.postValue(false)
                    }
                    MainActivity.isLoggedIn.postValue(false)
                }
            }
        }

        val isEnabled =
            isAccessibilityServiceEnabled(requireContext(), TouchAccessibilityService::class.java)
        if (!isEnabled) {
            val dialog =
                packageDialog(getString(R.string.disclaimer), Settings.ACTION_ACCESSIBILITY_SETTINGS)
            dialog.show()
        }

        mainViewModel.isOnPastOnBoarding(true)

        resizeViews()
        setOnTouchListenerRecord()
        setOnTouchListenerPause()

        manager =
            activity?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (arguments?.getString("action") == ACTION_SHOW_RECORDING_FRAGMENT_IMM_RECORD) {
            toggleRecording()
        }

        subscribeToObservers()
    }

    private fun requestScreenCapture() {
        val mediaProjectionManager = requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionCallback?.onMediaProjectionRequested()
        startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startScreenshotService(resultCode: Int, data: Intent?) {
        if (data == null) return

        mediaProjectionCallback?.onMediaProjectionResult(resultCode, data)

        val serviceIntent = Intent(requireContext(), ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenshotService.EXTRA_DATA, data)
            // Check if we're restarting after permission loss
            if (restartProjection) {
                action = ScreenshotService.ACTION_RESTART_PROJECTION
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this@ScreenRecordFragment.requireContext(), serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }

        Toast.makeText(requireContext(), "Screenshot service started", Toast.LENGTH_SHORT).show()

        // Notify activity if it was opened just for restart
        if (restartProjection && activity != null) {
            activity?.finish()
        }
    }

    private fun resizeViews() {
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)

        val barMargin = 10
        val boxTopBottomMargin = 20
        val height = displayMetrics.heightPixels
        val boxHeight = height / 8
        val width = displayMetrics.widthPixels - barMargin * 5

        // Resize boxOne and its text components
        val params1: ViewGroup.MarginLayoutParams =
            binding.boxOne.layoutParams as LinearLayout.LayoutParams
        params1.width = width / 3
        params1.topMargin = boxTopBottomMargin
        params1.bottomMargin = boxTopBottomMargin
        binding.boxOne.layoutParams = params1

        val textParams: ViewGroup.MarginLayoutParams =
            binding.textBoxOne.layoutParams as LinearLayout.LayoutParams
        textParams.width = width / 3
        binding.textBoxOne.layoutParams = textParams
        binding.textTopBoxOne.layoutParams = textParams

        // Resize boxTwo and its text components
        val params2: ViewGroup.MarginLayoutParams =
            binding.boxTwo.layoutParams as LinearLayout.LayoutParams
        params2.width = width / 3
        params2.topMargin = boxTopBottomMargin
        params2.bottomMargin = boxTopBottomMargin
        binding.boxTwo.layoutParams = params2

        binding.textBoxTwo.layoutParams = textParams
        binding.textTopBoxTwo.layoutParams = textParams

        // Resize boxThree and its text components
        val params3: ViewGroup.MarginLayoutParams =
            binding.boxThree.layoutParams as LinearLayout.LayoutParams
        params3.width = width / 3
        params3.topMargin = boxTopBottomMargin
        params3.bottomMargin = boxTopBottomMargin
        binding.boxThree.layoutParams = params3

        binding.textBoxThree.layoutParams = textParams
        binding.textTopBoxThree.layoutParams = textParams

        // Adjust margins for the bar between boxes
        val paramsBar1: ViewGroup.MarginLayoutParams =
            binding.boxOne.layoutParams as ViewGroup.MarginLayoutParams
        paramsBar1.leftMargin = barMargin
        paramsBar1.rightMargin = barMargin

        val paramsBar2: ViewGroup.MarginLayoutParams =
            binding.boxTwo.layoutParams as ViewGroup.MarginLayoutParams
        paramsBar2.leftMargin = barMargin
        paramsBar2.rightMargin = barMargin

        // Resize bar between boxOne and boxTwo
        val barParamsSize1: ViewGroup.MarginLayoutParams =
            binding.boxOneBar.layoutParams as LinearLayout.LayoutParams
        barParamsSize1.width = 5
        barParamsSize1.topMargin = 30
        barParamsSize1.bottomMargin = 30
        binding.boxOneBar.layoutParams = barParamsSize1

        // Resize bar between boxTwo and boxThree
        val barParamsSize2: ViewGroup.MarginLayoutParams =
            binding.boxTwoBar.layoutParams as LinearLayout.LayoutParams
        barParamsSize2.width = 5
        barParamsSize2.topMargin = 30
        barParamsSize2.bottomMargin = 30
        binding.boxTwoBar.layoutParams = barParamsSize2
    }


    private fun isAccessibilityServiceEnabled(
        context: Context,
        service: Class<out AccessibilityService?>
    ): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName.equals(context.packageName) && enabledServiceInfo.name.equals(
                    service.name
                )
            ) return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        cloudAuthentication.fetchCurrentAuthSession()
        lifecycleScope.launch {
            HardwareChecks.isConnectedAsync(WeakReference(requireContext()))
        }
    }

    private fun checkConnectionStatus(): Boolean {
        return if (HardwareChecks.isConnected(requireContext())) {
            MainActivity.isWifiConnected.postValue(true)
            true
        } else {
            MainActivity.isWifiConnected.postValue(false)
            false
        }
    }

    private fun wifiUIChange(isConnected: Boolean) {
        binding.statusBar.setBackgroundResource(if (isConnected) R.color.green else R.color.dark)
        binding.onlineStatus.setTextColor(
            resources.getColor(R.color.white)
        )
        binding.onlineStatus.text = if (isConnected) getString(R.string.online) else getString(R.string.offline)
    }

    private fun showPauseDialog() {
        val mapOfEntrees = mapOf(
            "1 Minute" to 60000L,
            "2 Minutes" to 120000L,
            "4 Minutes" to 240000L,
            "8 Minutes" to 480000L,
            "10 Minutes" to 600000L
        )
        val listItems = mapOfEntrees.keys.toTypedArray()
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Pause for")
            .setIcon(R.drawable.ic_pause_48px)
            .setSingleChoiceItems(listItems, -1) { _, i ->
                ScreenshotService.pauseTiming.postValue(mapOfEntrees[listItems[i]])
            }
            .setCancelable(false)
            .setPositiveButton(getString(R.string.done)) { _, _ -> pauseRecording() }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.cancel() }
            .create()
        builder.show()
    }

    private fun pauseRecording() {
        ScreenshotService.isPaused.postValue(true)
        sendCommandToRecordService(ACTION_PAUSE_SERVICE)
    }

    private fun sendCommandToRecordService(action: String) =
        Intent(requireContext(), ScreenshotService::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ContextCompat.startForegroundService(requireContext(), this)
        }

    // 1. In your Activity/Fragment (where you want to trigger the stop)
    fun stopForegroundService() {
        val stopIntent = Intent(context, ScreenshotService::class.java).apply {
            // Define a custom action to signal stopping
            action = "ACTION_STOP_SERVICE"

            // Add flags for reliable delivery
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Send the intent to the service
        context?.startService(stopIntent)
    }

    private fun clearButtonExplainerText() {
        binding.buttonExplainerText.text = ""
        binding.explainerText.text = ""
    }

    private fun updateRecording(isRecording: Boolean) {
        this.isRecording = isRecording
    }

    private fun subscribeToObservers() {
        val backgroundColorTransition = binding.loginFragmentRecordBack.background as TransitionDrawable

        ScreenshotService.isRunning.observe(viewLifecycleOwner, Observer {
            updateRecording(it)
            binding.apply {
                if (it) {
                    innerImage.setBackgroundResource(R.drawable.stop_button)
                    explainerText.text = getString(R.string.recording)
                    if (innerImage.tag == "record_main") {
                        backgroundColorTransition.startTransition(CHANGE_BACKGROUND_ANIMATION_TIME)
                    }
                    innerImage.tag = "stop_main"
                    buttonExplainerText.text = ""
                } else {
                    innerImage.setBackgroundResource(R.drawable.record_main)
                    explainerText.text = getString(R.string.not_recording)
                    if (innerImage.tag == "stop_main") {
                        backgroundColorTransition.reverseTransition(CHANGE_BACKGROUND_ANIMATION_TIME)
                    }
                    innerImage.tag = "record_main"
                }
            }
        })

        ScreenshotService.manualUploadPercentComplete.observe(viewLifecycleOwner, Observer {
            binding.determinateBar.progress = it.toInt()
        })

        ScreenshotService.uploadTotal.observe(viewLifecycleOwner, Observer {
            binding.textBoxOne.text = it.toString()
        })

        ScreenshotService.uploadedThisWeek.observe(viewLifecycleOwner, Observer {
            binding.textBoxThree.text = it.toString()
        })

        ScreenshotService.notUploaded.observe(viewLifecycleOwner, Observer {
            binding.textBoxTwo.text = it.toString()
        })

        ScreenshotService.uploadCountMsg.observe(viewLifecycleOwner, Observer {
            binding.tvUpdates.text = it.toString()
        })

        ScreenshotService.isPaused.observe(viewLifecycleOwner, Observer {
            if (it) {
                clearButtonExplainerText()
                changePauseToCancel()
            } else {
                clearButtonExplainerText()
                changeCancelToPause()
            }
        })

        ScreenshotService.pausedTimer.observe(viewLifecycleOwner, Observer {
            binding.explainerText.text = it
        })

        MainActivity.isWifiConnected.observe(viewLifecycleOwner, Observer {
            wifiUIChange(it)
        })

        MainActivity.isLoggedIn.observe(viewLifecycleOwner, Observer {
            if (!it && MainActivity.isLoggedOut.value == true) {
                cloudAuthentication.signOut(MainActivity.isLoggedOut)
                stopRecording()
                findNavController().navigate(R.id.loginFragment)
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setOnTouchListenerRecord() {
        setupTouchListener(
            view = binding.innerImage,
            enlargeAction = { enlargeButton(binding.innerImage) },
            alphaReset = { binding.innerImage.alpha = 1f },
            isTouchedFlag = { isTouched },
            setIsTouchedFlag = { isTouched = it },
            runnableAction = {
                if (binding.innerImage.tag == "record_main") {
                    binding.innerImage.alpha = 1f
                    triggerVibrateRecordOptions()
                    toggleRecording()
                } else {
                    binding.innerImage.alpha = 1f
                    triggerVibrateRecordOptions()
                    stopRecording()
                    binding.buttonExplainerText.text = ""
                    if (ScreenshotService.isPaused.value == true) {
                        changeCancelToPause()
                    }
                }
            },
            textUpdateAction = {
                binding.buttonExplainerText.text =
                    if (binding.screenRecordFragmentPause.tag == "play_main") "" else "hold to record"
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setOnTouchListenerPause() {
        setupTouchListener(
            view = binding.screenRecordFragmentPause,
            enlargeAction = { enlargeButtonPause(binding.screenRecordFragmentPause) },
            alphaReset = { binding.screenRecordFragmentPause.alpha = 1f },
            isTouchedFlag = { isTouchedPause },
            setIsTouchedFlag = { isTouchedPause = it },
            runnableAction = {
                if (!ScreenshotService.isRunning.isInitialized || ScreenshotService.isRunning.value == false) {
                    Toast.makeText(activity, "Must be recording to pause", Toast.LENGTH_SHORT).show()
                    return@setupTouchListener
                }

                if (binding.screenRecordFragmentPause.tag == "pause_main") {
                    if (!ScreenshotService.isRunning.value!!) {
                        Toast.makeText(activity, "Not Recording.", Toast.LENGTH_LONG).show()
                        return@setupTouchListener
                    }
                    triggerVibrateRecordOptions()
                    showPauseDialog()
                } else {
                    changeCancelToPause()
                    triggerVibrateRecordOptions()
                    ScreenshotService.isPaused.postValue(false)
                }
            },
            textUpdateAction = {
                binding.buttonExplainerText.text =
                    if (binding.screenRecordFragmentPause.tag == "play_main") "hold to cancel" else "hold to pause"
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener(
        view: View,
        enlargeAction: () -> Unit,
        alphaReset: () -> Unit,
        isTouchedFlag: () -> Boolean,
        setIsTouchedFlag: (Boolean) -> Unit,
        runnableAction: () -> Unit,
        textUpdateAction: () -> Unit
    ) {
        view.setOnTouchListener { _, event ->
            val action = event?.action

            if (isTouchedFlag()) {
                handler = Handler(Looper.getMainLooper())
                val runnable = Runnable {
                    runnableAction()
                }
                enlargeAction()
                handler?.postDelayed(runnable, holdDelayMillis)
                setIsTouchedFlag(false)
                view.alpha = 0.5f
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                alphaReset()
                setIsTouchedFlag(true)
                textUpdateAction()
                handler?.removeCallbacksAndMessages(null)
                return@setOnTouchListener true
            }
            true
        }
    }

    private fun enlargeButton(image: ImageView){
        animator.duration = holdDelayMillis
        animator.addUpdateListener { animation ->
            image.scaleX = animation.animatedValue as Float
            image.scaleY = animation.animatedValue as Float
        }

        animator.repeatCount = 0
        animator.repeatMode = ValueAnimator.REVERSE
        animator.start()
    }

    private fun enlargeButtonPause(image: ImageView){
        animPause.duration = holdDelayMillis
        animPause.addUpdateListener { animation ->
            image.setScaleX(animation.animatedValue as Float)
            image.setScaleY(animation.animatedValue as Float)
        }
        animPause.repeatCount = 0
        animPause.repeatMode = ValueAnimator.REVERSE
        animPause.start()
    }


    private fun toggleRecording() {

        lifecycleScope.launch {
            val user = userDao.getUser()
            if(user.emailHash.isNullOrEmpty()) {
                showMissingIdDialog()
            }else{
                requestPermissions()
                requestPackageUsageStatsPermissions()
                startRecording()
            }
        }
    }

    private fun requestPermissions() {
        if (!EasyPermissions.hasPermissions(
                requireContext(),
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET
            )
        ) {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.you_must_accept_all_permissions_for_the_app_to_work),
                PERMISSIONS_REQUEST_CODE,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET
            )
        }
    }

    private fun hasPackageUsageStatsPermissions(): Boolean {
        return try {
            val packageManager: PackageManager =
                (context?.packageManager ?: activity) as PackageManager
            val applicationInfo =
                context?.let { packageManager.getApplicationInfo(it.packageName, 0) }
            val appOpsManager = context?.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
            var mode = 0
            if (applicationInfo != null) {
                mode = appOpsManager!!.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid, applicationInfo.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun hasAllPermissionsAccepted(): Boolean {
        return (hasPackageUsageStatsPermissions())
    }

    private fun startRecording() {
        if (hasAllPermissionsAccepted()) {
            //sendCommandToRecordService(ACTION_START_OR_RESUME_SERVICE)
            //user must allow casting'
            // requestScreenCastPermissions()
            requestScreenCapture()
//            //blocking dialog to wait for user to accept cast permissions
        }
    }

    private fun requestPackageUsageStatsPermissions() {

        if (!hasPackageUsageStatsPermissions()) {
            val dialog = packageDialog(
                getString(R.string.for_screenlake_to_work_properly_you_must_manually_permit_usage_access_this_is_so_we_can_stop_recording_data_sensitive_apps_for_privacy_reasons),
                Settings.ACTION_USAGE_ACCESS_SETTINGS
            )
            dialog.show()
        }
    }

    private fun packageDialog(message: String, action: String): androidx.appcompat.app.AlertDialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.screenlake))
            .setMessage((message))
            .setIcon(R.drawable.logo_just_square_small)
            .setPositiveButton(getString(R.string.agree)) { _, _ ->

                val intent = Intent(action)
                startActivity(intent)
            }

            .setNegativeButton(getString(R.string.not_now)) { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
    }

    private fun triggerVibrateRecordOptions() {
        val vibe = activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibe.vibrate(150)
    }

    private fun changePauseToCancel() {
        binding.screenRecordFragmentPause.setImageResource(R.drawable.cancel_48px)
        binding.screenRecordFragmentPause.tag = "play_main"
    }

    private fun changeCancelToPause() {
        binding.screenRecordFragmentPause.setImageResource(R.drawable.ic_pause_48px)
        binding.screenRecordFragmentPause.tag = "pause_main"
    }

    private fun stopRecording() {
        ScreenshotService.isRunning.postValue(false)
        stopForegroundService()
        Toast.makeText(activity, getString(R.string.recording_stopped), Toast.LENGTH_LONG).show()
        lifecycleScope.launch { saveLog(RECORD_TRIGGERED, "false") }
    }

    private fun uploadCommand() = CoroutineScope(Dispatchers.IO).launch {
        ScreenshotService.manualOcr.postValue(true)
    }


    private suspend fun saveLog(event: String, msg: String) {
        logEventDao.saveException(
            LogEventEntity(event, msg, amplifyRepository.email)
        )
    }

    override fun onPermissionsGranted(p0: Int, p1: MutableList<String>) {
        TODO("Not yet implemented")
    }

    override fun onPermissionsDenied(p0: Int, p1: MutableList<String>) {
        TODO("Not yet implemented")
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this@ScreenRecordFragment.requireContext(),
            getString(R.string.screen_recording_permission_was_denied_cannot_start_screen_recording), Toast.LENGTH_LONG).show()
    }

    private fun showMissingIdDialog() {
        // Get the layout inflater
        val context = this@ScreenRecordFragment.requireContext()
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_missing_id, null)

        // Get the EditText from the inflated view
        val codeEditText = dialogView.findViewById<EditText>(R.id.codeEditText)

        // Build the dialog
        val builder = AlertDialog.Builder(context)
            .setTitle("Missing ID")
            .setMessage("Please enter your 4 digit panel invite code before continuing.")
            .setPositiveButton("Continue") { dialog, _ ->
                val inviteCode = codeEditText.text.toString()
                if (inviteCode.isNotEmpty()) {

                    if (!validateCode(inviteCode)) {
                        Toast.makeText(context, "Error -> Please enter a valid 4 digit invite code", Toast.LENGTH_SHORT).show()
                    } else {
                        // Process the invite code
                        lifecycleScope.launch {
                            val user = userDao.getUser()
                            user.emailHash = inviteCode

                            userDao.insertUserObj(user)
                            ScreenshotService.user = user
                        }

                        Toast.makeText(context, "Invite code: $inviteCode", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } else {
                    Toast.makeText(context, "Please enter a valid invite code", Toast.LENGTH_SHORT).show()
                    // Keep the dialog open
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setView(dialogView)


        // Show the dialog
        builder.create().show()
    }

    fun validateCode(code: String): Boolean {
        if (code.length != 4) {
            return false
        }
        if (!code.all { it.isDigit() }) {
            return false
        }
        val numericCode = code.toInt()
        return numericCode in 0..9999
    }

    companion object {
        private const val CHANGE_BACKGROUND_ANIMATION_TIME = 350

        fun newInstance(): ScreenRecordFragment {
            return ScreenRecordFragment()
        }
    }
}