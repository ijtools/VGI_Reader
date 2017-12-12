# VGI_Reader
VGI File Reader for ImageJ

This is a simple reader for VGI file as provided by VGStudio Software. 

The Image file is composed of two parts:
* the VGI file, that contains meta-data
* the vol file, that contains the raw data

To install the plugin, simply copy the file into the "plugins" folder of ImageJ.

The plugin parses the VGI file, and read data in the raw files. Data are stoed in a new instance of ImagePlus, and displayed within ImageJ.
