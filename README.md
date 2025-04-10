# Dynamsoft BarcodeScanner for Android and iOS Editions  

The **Dynamsoft BarcodeScanner** is a ready-to-use component built on the [Dynamsoft Barcode Reader SDK](https://www.dynamsoft.com/barcode-reader/overview/). This repository contains the source code for both Android and iOS editions, enabling developers to integrate barcode scanning functionality into their mobile applications easily.  

## Features  

- High-performance barcode scanning with support for various symbologies.  
- Easy integration for Android and iOS projects.  

## How to Use  

### **Android Integration**  

1. **Download Source Code**  
   - Navigate to the `android/src` folder in this repository and download the source code. Modify the code according to your project requirements.  

2. **Import `DynamsoftBarcodeReaderBundle/dynamsoftbarcodereaderbundle` as a Module**
   - In Android Studio, go to File > New > Import Module.
   - Select the `DynamsoftBarcodeReaderBundle/dynamsoftbarcodereaderbundle` directory and follow the prompts to add it to your project.

3. **Add the module dependency to your app's `build.gradle` file:**  

     ```groovy
     dependencies {
         implementation project(':dynamsoftbarcodereaderbundle')
     }
     ```

4. **Sync Your Project**  
   - Sync the Gradle files to ensure the dependency is correctly loaded.  

### **iOS Integration**  

1. **Download Source Code**  
   - Navigate to the `ios/src` folder in this repository and download the source code. Modify the code as per your project needs.  

2. **Add `DynamsoftBarcodeReaderBundle` to Your Xcode Project**  
   - Drag the `DynamsoftBarcodeReaderBundle.xcodeproj` into your Xcode project.

3. **Verify Dependencies**  
   - Verify that the necessary libraries and frameworks (e.g., `DynamsoftCore`, `DynamsoftBarcodeReader`, etc.) are linked in your Xcode project's settings.  

## Support  

For assistance, feel free to contact us:  

- **Website**: [https://www.dynamsoft.com](https://www.dynamsoft.com)  
- **Contact**: [https://www.dynamsoft.com/company/contact/](https://www.dynamsoft.com/company/contact/)  
