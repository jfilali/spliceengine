/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.encoding;

import com.splicemachine.utils.ByteSlice;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Arrays;

/**
 * A raw encoder to encode data into a single, automatically expanding byte array.
 *
 * @author Scott Fines
 *         Date: 1/19/15
 */
public class ExpandingEncoder {
    private byte[] buffer;
    private int currentOffset;

    private final float resizeFactor;

    public ExpandingEncoder(int initialSize, float resizeFactor){
        this.buffer = new byte[initialSize];
        this.resizeFactor = resizeFactor;
        this.currentOffset = 0;
    }

    public ExpandingEncoder(int initialSize) {
        this(initialSize,1.5f);
    }

    public ExpandingEncoder(float resizeFactor) {
        this(10,resizeFactor);
    }

    public ExpandingEncoder encode(byte value){
        ensureCapacity(Encoding.encodedLength(value));
        currentOffset+=Encoding.encode(value,buffer,currentOffset);
        return this;
    }

    public ExpandingEncoder rawEncode(byte value){
        ensureCapacity(1);
        buffer[currentOffset] = value;
        currentOffset++;
        return this;
    }

    public ExpandingEncoder encode(short value){
        ensureCapacity(Encoding.encodedLength(value));
        currentOffset+=Encoding.encode(value,buffer,currentOffset);
        return this;
    }

    public ExpandingEncoder encode(int value){
        ensureCapacity(Encoding.encodedLength(value));
        currentOffset+=Encoding.encode(value,buffer,currentOffset);
        return this;
    }

    public ExpandingEncoder encode(String value){
        ensureCapacity(value.length());
        /*
         * We add an extra 0x00 here to delimit the end of a String, so that
         * we can easily parse  list of Strings easily.
         */
        currentOffset+=Encoding.encode(value,buffer,currentOffset)+1;
        return this;
    }

    public ExpandingEncoder rawEncode(byte[] value){
        return rawEncode(value,0,value.length);
    }

    public ExpandingEncoder rawEncode(byte[] value, int offset, int length){
        ensureCapacity(length+Encoding.encodedLength(length));
        currentOffset+=Encoding.encode(length,buffer,currentOffset);
        assert currentOffset+length<=buffer.length: "Did not ensure enough capacity!";
        if(value != null) {
            System.arraycopy(value, offset, buffer, currentOffset, length);
        }
        currentOffset+=length;

        return this;
    }

    public ExpandingEncoder rawEncode(ByteSlice byteSlice){
        return rawEncode(byteSlice.array(),byteSlice.offset(),byteSlice.length());
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP",justification = "Intentional")
    public byte[] getBuffer(){
        if(currentOffset<buffer.length){
            byte[] newBytes = new byte[currentOffset];
            System.arraycopy(buffer,0,newBytes,0,currentOffset);
            return newBytes;
        }else
            return buffer;
    }

    /****************************************************************************************************************/
    /*private helper methods*/
    private void ensureCapacity(int requiredLength) {
        if(buffer.length-currentOffset>requiredLength) return; //we have enough space, no worries!

        long len = buffer.length;
        do {
            len = (long)(resizeFactor * len);
        }while(len-currentOffset<requiredLength);

        int newSize;
        if(len>Integer.MAX_VALUE)
           newSize = Integer.MAX_VALUE;
        else
            newSize = (int)len;
        buffer = Arrays.copyOf(buffer,newSize);
    }


}
