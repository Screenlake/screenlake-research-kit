screenlake.md
# Screenlake Research Kit
## Android Screen Capture & OCR App

## Table of Contents
1. [Overview of App Technical Capacities](#overview-of-app-technical-capacities)
2. [How to Configure the App for Your Own Cloud (AWS Only)](#how-to-configure-the-app-for-your-own-cloud-aws-only)
3. [Run Tests After Any Change](#how-to-run-tests)
4. [How to Distribute the App](#how-to-distribute-the-app)

## More information
1. [Contributing](docs/CONTRIBUTING.md)
2. [Code of Conduct](docs/CODE_OF_CONDUCT.md)

## Overview of App Technical Capacities
This Android application is designed to take screenshots of the device screen every 3 seconds and perform Optical Character Recognition (OCR) to extract text from the captured images. Additionally, the app collects a variety of data related to the device and the apps running on it:

- **Screenshots & OCR Processing:** Captures screen content every 3 seconds and uses OCR to extract text from the screenshots.
- **App Log Data:** Records metadata such as app names and timestamps at the time of each screenshot.
- **App Usage Data:** Collects detailed usage data from the device, including which apps are running and how long they’ve been used.
- **App Segment Data:** Tracks and logs data showing the last five apps that were on the screen before the current one.
- **Phone Session Data:** Monitors phone session data, recording everything from the time the phone is unlocked to when it’s locked.
- **Accessibility Root View Data:** Captures accessibility data from the root view of the app to enhance data collection.
- **Cloud Upload & Configuration:** All collected data is uploaded to the cloud, and the app is configurable to work with your own cloud infrastructure, specifically AWS.

### Authentication
This app uses autheticated user pools within AWS to provision accounts for users. Users will need to provide an email and a group code to use the app.

![alt text](<images/sign_up.png>)

The following fields are configurable.
#### panelConfirmationCode
This code identifies your users. This is important because emails will not be associated with user data, only the code entered here. The code must be a 4 digit number.

#### termsOfService
This field is a URL pointing to your Terms of Service.

### Explainer
The below is configurable and lets users know during on boarding what the app is used for.

![alt text](<images/onboarding_explainer.png>)


### How to Configure the App for Your Own Cloud (AWS Only)

#### Create Cognito User Pool
In order to authenticate users, you need to create a userpool in AWS. Follow the [user-pool-creation-aws.md](docs/USER-POOL-CREATION-AWS.md) tutorial.

#### Create Cognito Identity
In order allow users access to aws resources, you need to create a cognito identity in AWS. Follow the [identity-creation-aws.md](docs/IDENTITY-CREATION-AWS.md) tutorial.

#### Create S3 Bucket for Storage
In order to store screenshots and data sets, you need to create an S3 in bucket in AWS. Follow the [s3-bucket-creation-aws.md](docs/S3-BUCKET-CREATION.md) tutorial.

#### Configure App for AWS
You will need to configure the app to work with your AWS resources. To do this, you need to update the local.properties file with the following fields:
    
```
AMAZON_REGION_NAME=
AMAZON_BUCKET_NAME=
COGNITO_IDENTITY_POOL_ID=
COGNITO_POOL_ID=
COGNITO_APP_CLIENT_ID=
```

##### AMAZON_REGION_NAME: 
The AWS region where your Cognito User Pool, Identity Pool, and S3 bucket are hosted (e.g., us-west-1).
##### AMAZON_BUCKET_NAME: 
The name of your Amazon S3 bucket used for storing files or data (e.g., my-app-bucket).

##### COGNITO_IDENTITY_POOL_ID: 
The unique ID for your Cognito Identity Pool, enabling federated identity and temporary AWS credentials (e.g., us-west-1:12345678-90ab-cdef-1234-567890abcdef).

##### COGNITO_POOL_ID: 
The unique identifier for your Cognito User Pool used to manage and authenticate users (e.g., us-west-1_ABCdEfghI).

##### COGNITO_APP_CLIENT_ID: 
The ID assigned to your application for integrating with the Cognito User Pool (e.g., 12345abcdef67890).

##### WARNING
Do not check-in local.properties to any non-private versionsing system, this information should remain private. 


## Configurable Resources

### How to Change the App Logo in an Android App

### Step 1: Prepare Your Logo
1. **Design Your Logo**: Create or choose a logo image that represents your app. Ensure it is in PNG format with a transparent background for best results.
2. **Resize the Logo**: Prepare different sizes of your logo to accommodate various screen resolutions. Common sizes include:
    - **48x48 px** for low-density screens (ldpi)
    - **72x72 px** for medium-density screens (mdpi)
    - **96x96 px** for high-density screens (hdpi)
    - **144x144 px** for extra-high-density screens (xhdpi)
    - **192x192 px** for xx-high-density screens (xxhdpi)
    - **512x512 px** for the Google Play Store icon

### Step 2: Locate the Drawable Folder
1. **Open Android Studio**: Launch the app in Android Studio.
2. **Navigate to `res/drawable`**: In the project explorer, go to `app > src > main > res > drawable`. This is where you’ll store your logo files.

### Step 3: Replace the Existing Logo
1. **Copy Your Logo Files**: Copy your resized logo files into the respective drawable folders (`drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, etc.). If these folders don't exist, you can create them under the `res` directory.
2. **Rename the Files**: Ensure your logo files are named consistently across all drawable folders. The common name used is `ic_launcher.png`.
    - Example: `ic_launcher.png` for all resolutions.

### Step 4: Update the AndroidManifest.xml
1. **Open `AndroidManifest.xml`**: In the project explorer, navigate to `app > src > main > AndroidManifest.xml`.
2. **Locate the `<application>` Tag**: Inside the `<application>` tag, find the `android:icon` attribute.
3. **Set the New Icon**: Change the value of `android:icon` to the name of your new logo (without the file extension).
    - Example: `android:icon="@drawable/ic_launcher"`

### Step 5: Update the Adaptive Icons (Optional)
1. **Adaptive Icons**: For Android 8.0 (API level 26) and above, you should also update the adaptive icons. These consist of a foreground and background layer.
2. **Navigate to `res/mipmap-anydpi-v26`**: This folder contains the adaptive icon XML files.
3. **Edit the XML Files**: Modify `ic_launcher.xml` and `ic_launcher_round.xml` to point to your new foreground and background images.

### Step 6: Rebuild the Project
1. **Rebuild the Project**: Go to `Build > Rebuild Project` to apply the changes.
2. **Run Your App**: Deploy the app to an emulator or a device to see the new logo in action.

### Step 7: Test Your Logo
1. **Check Across Devices**: Test the logo on different devices and screen resolutions to ensure it looks good everywhere.


### Changing In App Text in the `res/strings` Resource Folder

1. **Locate the `strings.xml` File:**
    - The `strings.xml` file is located in the `res/values/` directory of our Android project. This file stores all the text used in the app as string resources.

2. **Open `strings.xml`:**
    - In Android Studio, navigate to the `res/values/strings.xml` file and open it.

3. **Modify or Add New String Resources:**
    - To change existing text, find the corresponding `<string>` element by its name and edit the text between the tags. For example:
      ```xml
      <string name="action_sign_in">Sign in or register</string>
      ```
    - To add a new string, create a new `<string>` element:
      ```xml
      <string name="action_sign_in">Sign in or register now</string>
      ```

### Changing Languages

1. **Create String Resources for Different Languages:**
    - For each language, create a new `strings.xml` file in a separate folder within `res/`. These folders are named using language and region codes, like `res/values-fr/` for French or `res/values-es/` for Spanish.
    - Example folder structure:
      ```
      res/
      ├── values/
      │   └── strings.xml
      ├── values-fr/
      │   └── strings.xml
      ├── values-es/
      │   └── strings.xml
      ```

2. **Add Translations:**
    - In each `strings.xml` file, provide the translation for each string. Ensure that the `name` attribute remains the same across all translations.
      ```xml
      <!-- English (default) -->
      <string name="welcome_message">Welcome to My Awesome App!</string>
 
      <!-- French -->
      <string name="welcome_message">Bienvenue dans mon application géniale!</string>
 
      <!-- Spanish -->
      <string name="welcome_message">¡Bienvenido a mi aplicación increíble!</string>
      ```

3. **Testing Language Change:**
    - To test your app in different languages, you can change the language setting on your Android device:
        - Go to **Settings** > **System** > **Languages & input** > **Languages**.
        - Add a new language or drag a preferred language to the top of the list.
    - The app should automatically load the appropriate `strings.xml` file based on the device's language setting.

   ### Running Tests

## Run Tests After Any Change

### Why Run Tests?

Running tests after making any changes to the codebase is crucial for maintaining the integrity and stability of the Android app. Here’s why:

- **Catch Bugs Early:** Automated tests help identify issues immediately after changes are made, preventing bugs from creeping into production.
- **Ensure Functionality:** Tests verify that both new and existing features work as expected, ensuring that new code doesn't break anything.
- **Improve Code Quality:** Regular testing promotes better coding practices and helps maintain a high standard of code quality.
- **Facilitate Refactoring:** With comprehensive tests in place, you can refactor your code confidently, knowing that your tests will catch any mistakes.

### How to Execute Tests

To run tests, follow these steps:

1. **Using Android Studio:**
    - **Unit Tests:**
        - Go to the **Project** view and expand the `src/test/java` directory.
        - Right-click on the test class or method you want to run.
        - Select **Run 'testName()'** or **Run 'All Tests'** to execute all tests.
    - **Instrumented Tests:**
        - Expand the `src/androidTest/java` directory for UI and instrumented tests.
        - Right-click on the test class or method.
        - Select **Run 'testName()'** or **Run 'All Tests'**.

2. **Using the Command Line:**
    - **Unit Tests:**
        - Navigate to your project’s root directory.
        - Run the following command to execute all unit tests:
          ```bash
          ./gradlew test
          ```
    - **Instrumented Tests:**
        - To run all instrumented tests on a connected device or emulator, use:
          ```bash
          ./gradlew connectedAndroidTest
          ```

3. **Review Test Results:**
    - After the tests complete, review the results in the **Run** or **Test** tab in Android Studio.
    - If using the command line, results will be displayed in the terminal, and detailed reports can be found in the `build/reports/` directory.

Regularly running and reviewing your tests ensures that your app remains reliable and performant as you continue to develop and refine it.

## How to Distribute the App
### Build the App
- Build your APK or AAB (Android App Bundle) using Android Studio or the command line.
- To generate an APK:
  ```bash
  ./gradlew assembleRelease
  ```
- To generate an AAB:
  ```bash
  ./gradlew bundleRelease
  ```

### Configure App Distribution in `build.gradle`
- Add the App Distribution configuration to the `build.gradle` (app-level):
  ```groovy
  appDistribution {
      serviceCredentialsFile="<path_to_your_service_credentials_file>.json"
      groups="testers-group"
  }
  ```

- Replace `<path_to_your_service_credentials_file>.json` with the path to your Firebase service account credentials file.
- The `groups` field should be the name of the tester group you’ve set up in Firebase.

### [Distribute Your App Through Firebase Distribution](https://firebase.google.com/docs/app-distribution)

### Upload the Build to Firebase
- Use the following command to upload your APK or AAB to Firebase App Distribution:
  ```bash
  ./gradlew appDistributionUploadRelease
  ```

### Notify Testers
- Once the upload is complete, your testers will automatically receive an email notification with instructions on how to download and install the app.

### Manage Distribution

- You can manage your releases and tester groups from the [Firebase console](https://firebase.google.com/docs/app-distribution). Here, you can see who has installed your app, resend invites, and view feedback from testers.

### Collect Feedback

- **Receive Feedback:**
    - Testers can provide feedback directly through the app, which is sent to the Firebase console. This feedback helps you identify and fix issues before your app is publicly released.

- **Review Crash Reports:**
    - If your app crashes during testing, Firebase Crashlytics will automatically collect and report the crash data, allowing you to debug and improve your app’s stability.
