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

package com.splicemachine.derby.impl.store;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.FormatableBitSet;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.derby.utils.marshall.dvd.DescriptorSerializer;
import com.splicemachine.derby.utils.marshall.dvd.VersionedSerializers;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.storage.ByteEntryAccumulator;
import com.splicemachine.storage.EntryPredicateFilter;
import com.carrotsearch.hppc.BitSet;
import java.io.IOException;

/**
 * @author Scott Fines
 * Date: 3/11/14
 */
public class ExecRowAccumulator extends ByteEntryAccumulator {
    protected final DataValueDescriptor[] dvds;
    protected final DescriptorSerializer[] serializers;
    protected final int[] columnMap;
    protected final int[] columnLengths;

    private ExecRowAccumulator(){
        super(null,false,null);
        this.dvds = null;
        this.serializers = null;
        this.columnMap = null;
        this.columnLengths = null;
    }

    private ExecRowAccumulator(EntryPredicateFilter predicateFilter,
                               boolean returnIndex,
                               BitSet fieldsToCollect,
                               DataValueDescriptor[] dvds,
                               int[] columnMap,
                               DescriptorSerializer[] serializers) {
        super(predicateFilter, returnIndex, fieldsToCollect);
        this.dvds = dvds;
        this.columnMap = columnMap;
        this.serializers = serializers;
        this.columnLengths = new int[dvds.length];
    }

    public static ExecRowAccumulator newAccumulator(EntryPredicateFilter predicateFilter,
                                                    boolean returnIndex,
                                                    ExecRow row,
                                                    int[] columnMap,
                                                    boolean[] columnSortOrder,
                                                    FormatableBitSet cols,
                                                    String tableVersion){
        DataValueDescriptor[] dvds = row.getRowArray();
        BitSet fieldsToCollect = new BitSet(dvds.length);
        boolean hasColumns = false;
        if(cols!=null){
            for(int i=cols.anySetBit();i>=0;i=cols.anySetBit(i)){
                hasColumns = true;
                fieldsToCollect.set(i);
            }
        }else if(columnMap!=null){
            for(int i=0;i<columnMap.length;i++){
                int pos = columnMap[i];
                if(pos<0) continue;
                hasColumns=true;
                if(dvds[pos]!=null)
                    fieldsToCollect.set(i);
            }
        }else{
            for(int i=0;i<dvds.length;i++){
                if(dvds[i]!=null){
                    hasColumns = true;
                    fieldsToCollect.set(i);
                }
            }
        }
        if(!hasColumns) return NOOP_ACCUMULATOR;

        DescriptorSerializer[] serializers = VersionedSerializers.forVersion(tableVersion,false).getSerializers(row);
        if(columnSortOrder!=null)
            return new Ordered(predicateFilter,returnIndex,fieldsToCollect,dvds,columnMap,serializers,columnSortOrder);
        else
            return new ExecRowAccumulator(predicateFilter,returnIndex,fieldsToCollect,dvds,columnMap,serializers);
    }

    public static ExecRowAccumulator newAccumulator(EntryPredicateFilter predicateFilter,
                                                    boolean returnIndex,
                                                    ExecRow row,
                                                    int[] columnMap,
                                                    FormatableBitSet cols,
                                                    String tableVersion){
        return newAccumulator(predicateFilter,returnIndex,row,columnMap,null,cols,tableVersion);
    }

    public static ExecRowAccumulator newAccumulator(EntryPredicateFilter predicateFilter,
                                                    boolean returnIndex,
                                                    ExecRow row,
                                                    int[] keyColumns,
                                                    String tableVersion){
        return newAccumulator(predicateFilter,returnIndex,row,keyColumns,null,tableVersion);
    }

    @Override
    protected void occupy(int position, byte[] data, int offset, int length) {
        decode(position, data, offset, length);
        super.occupy(position,data,offset,length);
    }

    @Override
    protected void occupyDouble(int position, byte[] data, int offset, int length) {
        decode(position, data, offset, length);
        super.occupyDouble(position, data, offset, length);
    }

    @Override
    protected void occupyFloat(int position, byte[] data, int offset, int length) {
        decode(position, data, offset, length);
        super.occupyFloat(position, data, offset, length);
    }

    @Override
    protected void occupyScalar(int position, byte[] data, int offset, int length) {
        decode(position,data,offset,length);
        super.occupyScalar(position, data, offset, length);
    }

    @Override
    public byte[] finish() {
        if(checkFilterAfter()) return null;
        return SIConstants.EMPTY_BYTE_ARRAY;
    }

    @Override
    public int getCurrentLength(int position){
        int colPos = columnMap[position];
        return columnLengths[colPos];
    }

    protected void decode(int position, byte[] data, int offset, int length) {
        int colPos=columnMap[position];
        DataValueDescriptor dvd = dvds[colPos];
        DescriptorSerializer serializer = serializers[colPos];
        try {
            serializer.decodeDirect(dvd, data, offset, length, false);
            columnLengths[colPos] = length; //stored for future length measuring
        } catch (StandardException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        for(DescriptorSerializer serializer:serializers){
            try{ serializer.close(); }catch(IOException ignored){ }
        }
    }

    private static class Ordered extends ExecRowAccumulator{

        private final boolean[] columnSortOrder;

        private Ordered(EntryPredicateFilter predicateFilter,
                        boolean returnIndex,
                        BitSet fieldsToCollect,
                        DataValueDescriptor[] dvds,
                        int[] columnMap,
                        DescriptorSerializer[] serializers,
                        boolean[] columnSortOrder) {
            super(predicateFilter, returnIndex, fieldsToCollect, dvds, columnMap, serializers);
            this.columnSortOrder = columnSortOrder;
        }

        @Override
        protected void decode(int position, byte[] data, int offset, int length) {
            int colPos=columnMap[position];
            DataValueDescriptor dvd = dvds[colPos];
            DescriptorSerializer serializer = serializers[columnMap[position]];
            try {
                serializer.decodeDirect(dvd, data, offset, length, !columnSortOrder[position]);
                columnLengths[colPos] = length;
            } catch (StandardException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final ExecRowAccumulator NOOP_ACCUMULATOR = new ExecRowAccumulator(){
        @Override protected void decode(int position, byte[] data, int offset, int length) { }
        @Override protected void occupy(int position, byte[] data, int offset, int length) { }
        @Override protected void occupyDouble(int position, byte[] data, int offset, int length) { }
        @Override protected void occupyFloat(int position, byte[] data, int offset, int length) { }
        @Override protected void occupyScalar(int position, byte[] data, int offset, int length) { }
        @Override public void reset() { }

        @Override public boolean isFinished() { return true; }
    };

}
