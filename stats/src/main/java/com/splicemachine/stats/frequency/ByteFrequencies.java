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

package com.splicemachine.stats.frequency;

import org.sparkproject.guava.collect.Sets;
import com.splicemachine.encoding.Encoder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 12/5/14
 */
class ByteFrequencies implements ByteFrequentElements {
    /*
     * MaxHeap of possible frequencies. This has a fixed size, so
     * as elements are added, elements which are too small to fit in the
     * list are moved out.
     */
    private Frequency[] heap;
    private final int maxSize;
    private int size;

    public ByteFrequencies(long [] counts, int k) {
        this.maxSize = k;
        int s =1;
        while(s<maxSize)
            s<<=1;
        this.heap = new Frequency[s];

        this.size = 0;
        for(int i=0;i<counts.length;i++){
            if(counts[i]>0)
                push(new Frequency((byte)i,counts[i]));
        }
    }

    private ByteFrequencies(Frequency[] heap, int maxSize,int size) {
        this.heap = heap;
        this.maxSize = maxSize;
        this.size = size;
    }

    @Override
    public ByteFrequencyEstimate countEqual(byte item) {
        for(int i=0;i<size;i++){
            Frequency frequency = heap[i];
            if(frequency!=null && frequency.value == item)
                return frequency;
        }
        return new Frequency(item,0l); //TODO -sf- can we avoid the object creation here?
    }

    @Override
    public Set<ByteFrequencyEstimate> frequentBetween(byte start, byte stop, boolean includeStart, boolean includeStop) {
        if(start==stop) {
            if(includeStart||includeStop) return Collections.singleton(countEqual(start));
            return Collections.emptySet();
        }
        Set<ByteFrequencyEstimate> frequencies = null;
        byte s = start;
        if(!includeStart)
            s++;
        int e = stop;
        if(includeStop)
            e++;
        for(int i=0;i<size;i++){
            Frequency f = heap[i];
            if(f!=null && f.value>=s){
                if(f.value<e){
                    //we can add it to the set
                    if(frequencies==null) frequencies = Sets.newTreeSet();
                    frequencies.add(f);
                }
            }
        }
        if(frequencies==null) return Collections.emptySet();
        else return frequencies;
    }

    @Override
    public Set<ByteFrequencyEstimate> frequentAfter(byte start, boolean includeStart) {
        return frequentBetween(start,Byte.MAX_VALUE,includeStart,true);
    }

    @Override
    public Set<ByteFrequencyEstimate> frequentBefore(byte stop, boolean includeStop) {
        return frequentBetween(Byte.MIN_VALUE, stop, true, includeStop);
    }

    @Override
    public ByteFrequentElements getClone() {
        Frequency[] heapCopy = Arrays.copyOf(heap,heap.length);
        return new ByteFrequencies(heapCopy,maxSize,size);
    }

    @Override
    public FrequencyEstimate<? extends Byte> equal(Byte item) {
        assert item!=null: "Cannot estimate frequencies for null item!";
        return countEqual(item.byteValue());
    }

    @Override
    public Set<? extends FrequencyEstimate<Byte>> frequentElementsBetween(Byte start, Byte stop, boolean includeMin, boolean includeStop) {
        byte s;
        byte e;
        if(start==null)
            s = Byte.MIN_VALUE;
        else s = start;
        if(stop==null)
            e = Byte.MAX_VALUE;
        else e = stop;
        return frequentBetween(s,e,includeMin,includeStop);
    }

    @Override
    public Set<? extends FrequencyEstimate<Byte>> allFrequentElements() {
        return frequentBetween(Byte.MIN_VALUE,Byte.MAX_VALUE,true,true);
    }

    @Override
    public long totalFrequentElements() {
        long totalRowSize=0;
        for (FrequencyEstimate<Byte> estimate : allFrequentElements()) {
            totalRowSize+=estimate.count();
        }
        return totalRowSize;
    }

    @Override
    public FrequentElements<Byte> merge(FrequentElements<Byte> other) {
        assert other instanceof ByteFrequencies: "Cannot merge instance of type "+ other.getClass();
        return merge((ByteFrequencies)other);
    }

    public ByteFrequentElements merge(ByteFrequentElements other) {
        /*
         * To perform the merge, we first get all the frequent elements in the other set,
         * then we combine those with our heap and create a new heap out of the result.
         */
        Set<ByteFrequencyEstimate> oFe = other.frequentBetween(Byte.MIN_VALUE,Byte.MAX_VALUE,true,true); //get everything
        Frequency[] oldHeap = heap;
        boolean[] oldValuesFound = new boolean[oldHeap.length];
        heap = new Frequency[oldHeap.length]; //keep the same number of entries
        int oldSize = size;
        size=0;
        for(ByteFrequencyEstimate oe: oFe){
            boolean found = false;
            for(int i=0;i<oldSize;i++){
                if(oldValuesFound[i]) continue; //we already saw this one
                Frequency f = oldHeap[i];
                if(f!=null && f.value==oe.value()){
                    f.count+=oe.count();
                    oldValuesFound[i]=true;
                    push(f); //push it into the new heap
                    found = true;
                    break;
                }
            }
            if(!found){
                //a new frequent element is found, so push it individually
                Frequency f = new Frequency(oe.value(),oe.count());
                push(f);
            }
        }
        for(int i=0;i<oldSize;i++){
            //push the old elements which haven't been found yet
            if(oldValuesFound[i]) continue;
            Frequency f = oldHeap[i];
            push(f);
        }
        return this;
    }

    /******************************************************************************************************************/
    /*private helper methods and classes*/
    private static class Frequency implements ByteFrequencyEstimate{
        byte value;
        long count;

        public Frequency(byte value, long count) {
            this.value = value;
            this.count = count;
        }

        @Override public byte value() { return value; }
        @Override public Byte getValue() { return value; }
        @Override public long count() { return count; }
        @Override public long error() { return 0; }

        @Override
        public FrequencyEstimate<Byte> merge(FrequencyEstimate<Byte> other) {
            this.count+=other.count();
            return this;
        }

        @Override
        @SuppressWarnings("NullableProblems")
        public int compareTo(ByteFrequencyEstimate o) {
            return value-o.value();
        }

        @Override
        public String toString() {
            return "("+value+","+count+",0)";
        }
    }

    private void push(Frequency frequency) {
        /*
         * we only want to push this frequency if it's larger than
         * the minimum element OR if the size < maxSize. Otherwise,
         * we just evict this element directly.
         */
        int pos;
        if(size<maxSize)
            pos=size;
        else if((frequency.count-heap[0].count)>0){
           pos = evict();
        }else{
            //don't bother adding this element, as it would be evicted anyway
            return;
        }
        while(pos>=0){
            int parentPos = (pos-1)/2;
            Frequency parent = heap[parentPos];
            if(parentPos==pos) {
                heap[pos] = frequency;
                break;
            }else if(parent==null) {
                /*
                 * If an element is deleted, it may leave holes which we don't necessarily
                 * clean up. As a result, we may encounter null fields. These should be treated as empty,
                 * and we should be allowed to move up a level
                 */
                pos = parentPos;
                continue;
            }
            long compare  = parent.count- frequency.count;
            if(compare<=0){
                /*
                 * we are >= the parent, so find the next open spot and insert
                 */
                heap[pos] = frequency;
                break;
            } else{
               /*
                * The parent is > than the element we are adding, so swap it with the parent,
                * and then recurse.
                *
                */
                heap[pos] = parent;
                pos=parentPos;
            }
        }
        size++;
    }

    private int evict() {
        /*
         * deleting an entry is relatively simple: remove the element at position p,
         * then choose the minimum from 2*p,2*p+1, and place it in position p, then
         * recurse to the new position.
         *
         * This method returns the nulled out position so that we can insert into that location
         */
        int pos = 0;
        while(pos<size){
            int left = 2*pos+1;
            int right = 2*pos+2;
            if(left>=maxSize){
                //there are no child elements to choose from, so just clear this entry and break
                heap[pos] = null;
                break;
            }else if(right>=maxSize){
                //there is only the left side of the tree to choose from, so that's the min
                heap[pos] = heap[left];
                break;
            }else{
                Frequency l = heap[left];
                Frequency r = heap[right];
                if(l==null){
                    //we exceeded our size anyway
                    heap[pos] = null;
                    break;
                }else if(r==null){
                    //the left is the only side to choose from, so that's the min
                    heap[pos] = heap[left];
                    pos = left;
                }else {
                    long compare = l.count - r.count;
                    if (compare <= 0) {
                        //the left is <= the right, so choose it and recurse
                        heap[pos] = l;
                        pos = left;
                    } else {
                        heap[pos] = r;
                        pos = right;
                    }
                }
            }
        }
        size--;
        return pos;
    }

    static class EncoderDecoder implements Encoder<ByteFrequencies> {
        public static final EncoderDecoder INSTANCE = new EncoderDecoder();

        @Override
        public void encode(ByteFrequencies item, DataOutput dataInput) throws IOException {
            dataInput.writeInt(item.maxSize);
            dataInput.writeInt(item.size);
            for(int i=0;i<item.size;i++){
                Frequency f = item.heap[i];
                dataInput.writeByte(f.value());
                dataInput.writeLong(f.count());
            }
        }

        @Override
        public ByteFrequencies decode(DataInput input) throws IOException {
            int maxSize = input.readInt();
            int size = input.readInt();
            int h = 1;
            while(h<maxSize)
                h<<=1;
            Frequency[] heap = new Frequency[h];
            for(int i=0;i<size;i++){
                byte v = input.readByte();
                long c = input.readLong();
                heap[i] = new Frequency(v,c);
            }
            return new ByteFrequencies(heap,maxSize,size);
        }
    }
}
