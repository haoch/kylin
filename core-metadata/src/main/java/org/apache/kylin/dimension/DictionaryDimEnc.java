/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.apache.kylin.dimension;

import java.nio.ByteBuffer;

import org.apache.kylin.common.util.Bytes;
import org.apache.kylin.common.util.BytesUtil;
import org.apache.kylin.metadata.datatype.DataTypeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DictionaryDimEnc extends DimensionEncoding {

    private static final Logger logger = LoggerFactory.getLogger(DictionaryDimEnc.class);

    // could use a lazy loading trick here, to prevent loading all dictionaries of a segment at once
    private final Dictionary<String> dict;
    private final int fixedLen;

    // used in encode(), when a value does not exist in dictionary
    private final int roundingFlag;
    private final byte defaultByte;

    public DictionaryDimEnc(Dictionary<String> dict) {
        this(dict, 0, NULL);
    }

    public DictionaryDimEnc(Dictionary<String> dict, int roundingFlag, byte defaultByte) {
        this.dict = dict;
        this.fixedLen = dict.getSizeOfId();
        this.roundingFlag = roundingFlag;
        this.defaultByte = defaultByte;
    }

    public int getRoundingFlag() {
        return roundingFlag;
    }
    
    public DictionaryDimEnc copy(int roundingFlag) {
        if (this.roundingFlag == roundingFlag)
            return this;
        else
            return new DictionaryDimEnc(dict, roundingFlag, defaultByte);
    }
    
    public DictionaryDimEnc copy(int roundingFlag, byte defaultByte) {
        if (this.roundingFlag == roundingFlag && this.defaultByte == defaultByte)
            return this;
        else
            return new DictionaryDimEnc(dict, roundingFlag, defaultByte);
    }
    
    public Dictionary<String> getDictionary() {
        return dict;
    }

    @Override
    public int getLengthOfEncoding() {
        return fixedLen;
    }

    @Override
    public void encode(byte[] value, int valueLen, byte[] output, int outputOffset) {
        try {
            int id = dict.getIdFromValueBytes(value, 0, valueLen, roundingFlag);
            BytesUtil.writeUnsigned(id, output, outputOffset, fixedLen);
        } catch (IllegalArgumentException ex) {
            for (int i = outputOffset; i < outputOffset + fixedLen; i++) {
                output[i] = defaultByte;
            }
            logger.error("Can't translate value " + Bytes.toString(value, 0, valueLen) + " to dictionary ID, roundingFlag " + roundingFlag + ". Using default value " + String.format("\\x%02X", defaultByte));
        }
    }

    @Override
    public String decode(byte[] bytes, int offset, int len) {
        int id = BytesUtil.readUnsigned(bytes, offset, len);
        try {
            String value = dict.getValueFromId(id);
            return value;
        } catch (IllegalArgumentException e) {
            logger.error("Can't get dictionary value from " + dict + " (id = " + id + ")");
            return "";
        }
    }

    @Override
    public DataTypeSerializer<Object> asDataTypeSerializer() {
        return new DictionarySerializer();
    }

    public class DictionarySerializer extends DataTypeSerializer<Object> {
        @Override
        public void serialize(Object value, ByteBuffer buf) {
            int id = dict.getIdFromValue(value == null ? null : value.toString(), roundingFlag);
            BytesUtil.writeUnsigned(id, dict.getSizeOfId(), buf);
        }

        @Override
        public Object deserialize(ByteBuffer in) {
            int id = BytesUtil.readUnsigned(in, dict.getSizeOfId());
            return dict.getValueFromId(id);
        }

        @Override
        public int peekLength(ByteBuffer in) {
            return dict.getSizeOfId();
        }

        @Override
        public int maxLength() {
            return dict.getSizeOfId();
        }

        @Override
        public int getStorageBytesEstimate() {
            return dict.getSizeOfId();
        }
    };
}
