package fastPercentiles;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PercentileRecorder {
    private int count = 0;
    private int range;
    private int[] data;
    private int minVal = -1;
    private int maxVal = -1;
    private final ReentrantLock dataLock = new ReentrantLock();

    public int getRange() {
        return range;
    }

    public int getCount() {
        return count;
    }

    public int[] getRawData() {
        return data;
    }

    public PercentileRecorder(int range) {
        this.range = range;
        this.data = new int[this.range];
        for (int i = 0; i < this.range; i++) {
            data[i] = 0;
        }
    }

    public void merge(int[] mergeData) {
        dataLock.lock();
        try {
            for (int i = 0; i < this.range; i++) {
                data[i] += mergeData[i];
            }
        } finally {
            dataLock.unlock();
        }
    }

    public void record(int value) {
        if (value > range) {
            throw new Error("Value" + value + " out of percentile ranges");
        }
        
        dataLock.lock();
        
        try {
            if (minVal == -1 || value < minVal) minVal = value;
            if (maxVal == -1 || value > maxVal) maxVal = value;
            
            this.data[value]++;
            this.count++;
        } finally {
            dataLock.unlock();
        }
    }

    @Override public String toString() {
        return "Percentile Recorder(" + range + "). Min: " + minVal + " Max:" + maxVal;
    }

    public Percentile[] percentiles() throws Exception {
        dataLock.lock();
        
        try {
            Percentile[] results = new Percentile[100];
            int partitionSize = count > 100 ? count / 100 : 1;
            for (int i=0; i < 100; i++) {
                results[i] = new Percentile(partitionSize);
            }

            if (count == 0) {
                dataLock.unlock();
                return results;
            }
            
            int percentileIdx = 0;
            Percentile curPercentile = results[percentileIdx];
            for (int value = minVal; value < maxVal; value++) {
                int valueCount = data[value];
                int qTaken = 0;
                int qLeft = valueCount;

                while (qLeft > 0) {
                    qTaken = curPercentile.spaceLeft() >= qLeft ? qLeft : curPercentile.spaceLeft();
                    qLeft -= qTaken;
                    
                    curPercentile.record(value, qTaken);
                    
                    if (curPercentile.isFull()) {
                        results[percentileIdx] = curPercentile;
                        
                        if (percentileIdx < 99 && qLeft > 0) {
                            percentileIdx++;                    
                            curPercentile = results[percentileIdx];
                        } else if (qLeft > 0) {
                            curPercentile.record(value, qLeft);
                            qLeft = 0;
                        }
                    }
                }
            }
            return results;
        } finally {
            dataLock.unlock();
        }
    }
}
