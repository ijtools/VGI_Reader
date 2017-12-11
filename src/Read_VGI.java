import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ShortProcessor;

/**
 * Lame implementation for reading VGI files produced by VG Studio Max software.
 * 
 * @author dlegland
 *
 */
public class Read_VGI implements PlugIn
{
	public Read_VGI()
	{
		
	}

	@Override
	public void run(String arg)
	{
		// get the file
		String path = arg;
		String directory = null;
		String fileName = null;

		if (null == path || 0 == path.length())
		{
			OpenDialog dlg = new OpenDialog("Choose .vgi file", null, "*.vgi");
			directory = dlg.getDirectory();
			if (null == directory)
				return;
			fileName = dlg.getFileName();
			path = directory + "/" + fileName;
		}
		else
		{
			// the argument is the path
			File fileIn = new File(path);
			directory = fileIn.getParent(); // could be a URL
			fileName = fileIn.getName();
		}

		if (!fileName.toLowerCase().endsWith(".vgi"))
		{
		    System.err.println("Expect input file extension to be .vgi, abort");
			return;
		}
		
		File file = new File(directory, fileName);
		ImagePlus imagePlus;
		try
		{
			imagePlus = readImage(file);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			IJ.error("Image import error", e.getMessage());
			return;
		}

		imagePlus.show();

		ImageStack stack = imagePlus.getStack();
		int currentSliceIndex = stack.size() / 2;
		imagePlus.setSlice(currentSliceIndex);
		stack.getProcessor(currentSliceIndex).resetMinAndMax();
	}




	public ImagePlus readImage(File file) throws IOException
	{
		String dataFileName = null;
		int sizeX = 0;
		int sizeY = 0;
		int sizeZ = 0;
		int bitDepth = 0;
		boolean littleEndian = true;
		
		try (LineNumberReader reader = new LineNumberReader(new FileReader(file))) 
		{ 
			String line;
			while ((line = reader.readLine()) != null)
			{
				line = line.trim();
				//			System.out.println(reader.getLineNumber() + ": " + line);

				if (line.startsWith("{") && line.endsWith("}"))
				{
					// Process new volume
					//				String volumeName = line.substring(1, line.length()-1);
					//				System.out.println("New volume: " + volumeName);
				}
				else if (line.startsWith("[") && line.endsWith("]"))
				{
					// Process new information block
					//				String blockName = line.substring(1, line.length()-1);
					//				System.out.println("  New block: " + blockName);

				}
				else
				{
					// process new key-value pair
					String[] tokens = line.split("=");
					if (tokens.length != 2)
					{
						System.err.println(String.format("Token count error at line %d: %s", reader.getLineNumber(), line));
						continue;
					}

					String key = tokens[0].trim();
					String valueString = tokens[1].trim();

					if ("size".equalsIgnoreCase(key))
					{
						tokens = valueString.split(" ");
						if (tokens.length != 3)
						{
							System.err.println(String.format("Assume three integer values at line %d: %s", reader.getLineNumber(), line));
							continue;
						}

						sizeX = Integer.parseInt(tokens[0]);
						sizeY = Integer.parseInt(tokens[1]);
						sizeZ = Integer.parseInt(tokens[2]);
					}
					else if ("bitsperelement".equalsIgnoreCase(key))
					{
						bitDepth = Integer.parseInt(valueString);
						if (bitDepth != 16)
						{
							throw new RuntimeException("Only 16 bits per elements are currently supported, not " + bitDepth);
						}
					}
					else if ("name".equalsIgnoreCase(key))
					{
						if (dataFileName == null)
						{
							System.out.println("data file name: "  + valueString);
							dataFileName = valueString;
						}
					}
				}
			}
			reader.close();
		};

		// assumes all necessary information have been read
		File dataFile = new File(dataFileName);
		dataFile = new File(file.getParentFile(), dataFile.getName());
		System.out.println("read data file: " + dataFile.getAbsolutePath());
		ImageStack stack = readData(dataFile, sizeX, sizeY, sizeZ, 16, littleEndian);
		
		return new ImagePlus(file.getName(), stack);
	}
	
	private ImageStack readData(File file, int sizeX, int sizeY, int sizeZ, int bitDepth, boolean littleEndian) throws IOException
	{
		// First validates the existence of the file
		if (!file.exists())
		{
			throw new RuntimeException("Could not find data file: " + file.getName());
		}

		// allocate stack
		IJ.showStatus("Create image stack");
		ImageStack stack = ImageStack.create(sizeX, sizeY, sizeZ, 16);
		
		IJ.showStatus("Read raw data");
		
		// allocate byte array for current slice 
		int pixelsPerPlane = sizeX * sizeY;
		int nBytes = pixelsPerPlane * 2;
		byte[] byteData = new byte[nBytes];
		
		try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file)))
		{
			// iterate over slices
			for (int z = 0; z < sizeZ; z++)
			{
				IJ.showProgress(z, sizeZ);
				// read current slice
				int nRead = inputStream.read(byteData, 0, nBytes);
				
				// check whole slice was correctly read
				if (nRead != nBytes)
				{
					throw new RuntimeException("Could read only " + nRead + " over the " + nBytes + " expected");
				}
				
				// convert byte array to ShortProcessor
				short[] data = convertToShortArray(byteData, littleEndian);
				ShortProcessor slice = new ShortProcessor(sizeX, sizeY, data, null);
				
				stack.setProcessor(slice, z+1);
			}

			inputStream.close();

			IJ.showProgress(1, 1);
			IJ.showStatus("");
		}

		return stack;
	}
	
	private short[] convertToShortArray(byte[] byteData, boolean littleEndian)
	{
		// allocate short array
		int size = byteData.length / 2;
		short[] data = new short[size];

		// convert byte pairs to shorts
		if (littleEndian)
		{
			for (int i = 0; i < size; i++)
			{
				byte b1 = byteData[2 * i];
				byte b2 = byteData[2 * i + 1];

				int v = ((b2 & 0xFF) << 8 | (b1 & 0x00FF));
				data[i] = (short) v;
			}
		} 
		else
		{
			for (int i = 0; i < size; i++)
			{
				byte b1 = byteData[2 * i];
				byte b2 = byteData[2 * i + 1];
				data[i] = (short) ((b1 & 0xFF) << 8 | (b2 & 0xFF));
			}
		}
		return data;
	}
	
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException
	{
		System.out.println("Test Read_VGI file");

		String fileName = "Grain_daa11.vgi";
		File file = new File("D:/images/wheat/perigrain/clermont/demo-tomo/grain_daa11/" + fileName);
		if (!file.exists())
		{
			System.err.println("Could not file file: " + fileName);
			return;
		}
		
		Read_VGI reader = new Read_VGI();
		ImagePlus imagePlus = reader.readImage(file);
		
		ImageStack stack = imagePlus.getStack();
		double value0 = stack.getVoxel(0, 0, 0);
		System.out.println("value at (0,0,0): " + value0);
		double value1 = stack.getVoxel(55, 0, 0);
		System.out.println("value at (55,0,0): " + value1);
		double value2 = stack.getVoxel(200, 100, 300);
		System.out.println("value at (200,100,300): " + value2);
		double value3 = stack.getVoxel(0, 0, 5);
		System.out.println("value at (0,0,5): " + value3);
		double value4 = stack.getVoxel(0, 0, 10);
		System.out.println("value at (0,0,10): " + value4);
		value1 = stack.getVoxel(55, 0, 0);
		System.out.println("value at (55,0,0): " + value1);
	}

}
