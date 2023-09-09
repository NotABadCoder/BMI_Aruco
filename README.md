# BMI_Aruco
Developed an app that calculates a person's height using Aruco markers .

A virtual scale is generated using Aruco markers .
Like id 9 is placed at particular height , then 8 , then so .
It atleast needs 2 markers for accurate measurements .

After that we do perspective correction of detected aruco markers using homography and wrapPerspective using OpenCv for more accuracy .

And we calculate the height of the person by detecting the face using haarcascade (OpenCv) and mapping it to the pixel per cm from Aruco markers. 

Also everything runs in the thread , Therefore no distortion of frames happens in the camera view .

Example for your reference 
![Android_BMI](https://github.com/NotABadCoder/BMI_Aruco/assets/110164850/bc2bd422-57b6-4877-8eec-8e0ffeb550ec)
