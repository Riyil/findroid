package org.findroid.data_types;

import org.rrlib.finroc_core_utils.rtti.DataType;
import org.rrlib.finroc_core_utils.serialization.InputStreamBuffer;
import org.rrlib.finroc_core_utils.serialization.OutputStreamBuffer;
import org.rrlib.finroc_core_utils.serialization.RRLibSerializableImpl;

public class Image extends RRLibSerializableImpl {

	private int height;
	private int width;
	private byte[] data;
	
    public final static DataType<Image> TYPE = new DataType<Image>(Image.class, "Image", false);
	
	enum Format {
        MONO8,
        MONO16,
        MONO32_FLOAT,
        RGB565,
        RGB24,
        BGR24,
        RGB32,
        BGR32,
        YUV420P,
        YUV411,
        YUV422,
        UYVY422,
        YUV444,
        BAYER_RGGB,
        BAYER_GBRG,
        BAYER_GRBG,
        BAYER_BGGR,
        HSV,
        HLS,
        HI240
    };
    
    public Image() {
    	this.height = 0;
    	this.width = 0;
    	this.data = new byte[0];
    }
    
    public Image(int height, int width, byte[] data) {
    	this.height = height;
    	this.width = width;
    	this.data = data;
    }
    
    public int getWidth() {
    	return width;
    }
    
    public int getHeight() {
    	return height;
    }
    
    public byte[] getData() {
    	return data;
    }
    
    public int getLength() {
    	return data.length;
    }
    
    public void setContent(int width, int height, byte[] data) {
    	this.width = width;
    	this.height = height;
    	this.data = data;
    }
	
	@Override
    public void serialize(OutputStreamBuffer os) {

        os.writeInt(width);
        os.writeInt(height);
        os.writeEnum(Format.YUV420P);
        os.writeInt(data.length);
        os.writeInt(0); // extra data size

        // region of interest
        os.writeBoolean(false);
        os.writeInt(0);
        os.writeInt(0);
        os.writeInt(0);
        os.writeInt(0);

        // Write image data
        os.write(data);
    }

	@Override
	public void deserialize(InputStreamBuffer is) {
		// TODO Auto-generated method stub
		
	}
	
}
