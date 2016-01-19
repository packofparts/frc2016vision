# frc2016vision
FRC 2016 Vision code running on a Raspberry Pi

# Raspberry Pi Setup Instructions

## Prepare SD card with NOOBS
1. Format the SD card as one big FAT32 partition
2. Download NOOBS from https://www.raspberrypi.org/downloads/noobs/
3. Unzip the zip file you downloaded, and copy all the files to the root of the SD card
4. Insert the card into the Pi and boot it

## Install Raspbian
1. Select English(US) for language and US for keyboard. 
2. Tick the checkbox next to Raspbian and click on Install.
3. Wait a long time for the instalation to be performed.

## Log in, configure, update, install, reboot

1. Log into using keyboard/mouse/monitor or via ssh. 
  * hostname: raspberrypi.local
  * username: pi
  * password: raspberry
2. Change the hostname
  * sudo nano /etc/hostname
  * Replace raspberrypi with raspberrypi-1294
  * Press CTRL+X to close the editor; agree to overwrite the existing file and save it.
  * sudo nano /etc/hosts
  * Leave all of the entries alone except for the very last entry labeled 127.0.1.1 with the hostname “raspberrypi“. This is the only line you want to edit. Replace “raspberrypi” with "raspberrypi-1294".
  * Press CTRL+X to close the editor; agree to overwrite the existing file and save it.
3. Configure to not launch X
  * sudo raspi-config
  * 3 Boot Options
  * B1 Console
  * Finish, Yes to reboot
4. Perform updates
  * Log back in using the new hostname, raspberrypi-1294.local.
  * sudo apt-get update
  * sudo apt-get upgrade
  * sudo rpi-update
5. Install prerequisites so we can build opencv
  * sudo apt-get install build-essential git cmake pkg-config ant
  * sudo apt-get install libjpeg-dev libtiff5-dev libjasper-dev libpng12-dev
  * sudo apt-get install libavcodec-dev libavformat-dev libswscale-dev libv4l-dev
  * sudo apt-get install libxvidcore-dev libx264-dev
  * sudo apt-get install libgtk-3-dev
  * sudo apt-get install libatlas-base-dev gfortran
  * sudo apt-get install python2.7-dev python3-dev
  
6. Get the sourcecode for opencv
  * cd ~
  * wget -O opencv.zip https://github.com/Itseez/opencv/archive/3.1.0.zip
  * unzip opencv.zip
  * wget -O opencv_contrib.zip https://github.com/Itseez/opencv_contrib/archive/3.1.0.zip
  * upzip opencv_contrib.zip

  
7. Get opencv ready to build
  * cd ~/opencv-3.1.0
  * mkdir build
  * cd build/
  * export JAVA_HOME=/usr/lib/jvm/jdk-8-oracle-arm-vfp-hflt
  * cmake -D CMAKE_BUILD_TYPE=RELEASE \
	-D CMAKE_INSTALL_PREFIX=/usr/local \
	-D INSTALL_C_EXAMPLES=OFF \
	-D INSTALL_PYTHON_EXAMPLES=ON \
	-D OPENCV_EXTRA_MODULES_PATH=~/opencv_contrib-3.1.0/modules \
	-D BUILD_EXAMPLES=ON ..

8. Build opencv
  * cd ~/opencv-3.1.0/build 
  * make -j4
  * Wait many many minutes (hours?)
  * sudo make install
  * sudo ldconfig
  
9. Deploy this project
  * Copy ./vision.service from this project to /etc/systemd/system on the RPi
  * Deploy this project, ./gradlew deploy
  * sudo systemctl enable vision.service
  
10. Reboot
  * sudo reboot
