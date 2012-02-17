package fastPercentiles;

/**
 * Created by IntelliJ IDEA.
 * User: andrewcholakian
 * Date: 2/16/12
 * Time: 7:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class Percentile {
    public int max = -1;
    public int min = -1;
    public int median = -1;
    public long total = 0;
    public int count = 0;
    public int partitionSize;

    public int getMax() {
        return max;
    }

    public int getMin() {
        return min;
    }

    public int getMedian() {
        return median;
    }

    public long getTotal() {
        return total;
    }

    public int getCount() {
        return count;
    }

    public int getPartitionSize() {
        return partitionSize;
    }

    public int getAvg() {
        return (total > 0 && count > 0) ? (int) (total / count) : 0;
    }

    public Percentile(int partitionSize) {
        this.partitionSize = partitionSize;
    }

    public void record(int value, int times) {
        if (times == 0) return;
        if (min == -1) min = value;
        max = value;

        count += times;
        total += value * times;

        if (median == -1 && count >= (partitionSize / 2)) {
            median = value;
        }
    }

    public int spaceLeft() {
        return partitionSize - count;
    }

    public boolean isFull() {
        return count >= partitionSize;
    }
}
