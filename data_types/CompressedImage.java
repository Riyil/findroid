package org.findroid.data_types;

import org.rrlib.finroc_core_utils.rtti.DataType;
import org.rrlib.finroc_core_utils.serialization.InputStreamBuffer;
import org.rrlib.finroc_core_utils.serialization.OutputStreamBuffer;
import org.rrlib.finroc_core_utils.serialization.RRLibSerializableImpl;

public class CompressedImage extends RRLibSerializableImpl {
    private byte[] compressedData;
    private int dataSize;

    public final static DataType<CompressedImage> TYPE = new DataType<CompressedImage>(CompressedImage.class);

    public CompressedImage() {
    	
    }
    
    public CompressedImage(byte[] data) {
        compressedData = data;
    }

    @Override
    public void deserialize(InputStreamBuffer is) {
        int size = is.readInt();
        if (compressedData.length < size) {
            compressedData = new byte[size * 2]; // keep some bytes for reserve...
        }
        dataSize = size;
        is.readFully(compressedData, 0, dataSize);
    }

    @Override
    public void serialize(OutputStreamBuffer os) {
        os.writeInt(dataSize);
        os.write(compressedData, 0, dataSize);
    }
    
    public void setBuffer(byte[] data) {
    	compressedData = data;
    	dataSize = data.length;
    }
}
