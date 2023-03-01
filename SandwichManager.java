import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/********************************************************************************************************************************
 * main thread
 ********************************************************************************************************************************/

public class SandwichManager {
    // static Object lock = new Object();
    static String logfile = "log.txt";

    public static void main(String[] args) {
        int sandwiches = Integer.parseInt(args[0]);
        int breadcap = Integer.parseInt(args[1]);
        int eggcap = Integer.parseInt(args[2]);
        int breadmakers = Integer.parseInt(args[3]);
        int eggmakers = Integer.parseInt(args[4]);
        int packers = Integer.parseInt(args[5]);
        int breadrate = Integer.parseInt(args[6]);
        int eggrate = Integer.parseInt(args[7]);
        int packingrate = Integer.parseInt(args[8]);

        try {
            FileWriter fw = new FileWriter(logfile, false);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("sandwiches: " + sandwiches);
            bw.newLine();
            bw.write("bread capacity: " + breadcap);
            bw.newLine();
            bw.write("egg capacity: " + eggcap);
            bw.newLine();
            bw.write("bread makers: " + breadmakers);
            bw.newLine();
            bw.write("egg makers: " + eggmakers);
            bw.newLine();
            bw.write("sandwich packers: " + packers);
            bw.newLine();
            bw.write("bread rate: " + breadrate);
            bw.newLine();
            bw.write("egg rate: " + eggrate);
            bw.newLine();
            bw.write("packing rate: " + packingrate);
            bw.newLine();
            bw.newLine();
            bw.flush();
            bw.close();
        } catch (IOException e) {
            System.out.println("invalid input");
        }

        Buffer breadbuffer = new Buffer(breadcap);
        Buffer eggbuffer = new Buffer(eggcap);
        
        Map<String, Integer> breadSummaryMap = new HashMap<String, Integer>();
        Map<String, Integer> eggSummaryMap = new HashMap<String, Integer>();
        Map<String, Integer> sandwichSummaryMap = new HashMap<String, Integer>();

        List<Thread> threads = new ArrayList<Thread>();

        // bread makers
        for (int i = 0; i < breadmakers; i++) {
            String makerid = "B" + i;
            MakeThread breadMaker = new MakeThread(FoodType.BREAD, makerid, breadbuffer, logfile, breadrate, sandwiches, breadSummaryMap, eggSummaryMap);
            threads.add(breadMaker);
        }

        // egg makers
        for (int i = 0; i < eggmakers; i++) {
            String makerid = "E" + i;
            MakeThread eggMaker = new MakeThread(FoodType.EGG, makerid, eggbuffer, logfile, eggrate, sandwiches, breadSummaryMap, eggSummaryMap);
            threads.add(eggMaker);
        }

        // packers
        for (int i = 0; i < packers; i++) {
            String packerid = "S" + i;
            PackerThread packer = new PackerThread(packerid, breadbuffer, eggbuffer, logfile, packingrate, sandwiches, sandwichSummaryMap);
            threads.add(packer);
        }

        // run worker threads
        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        //////////////////  count summary  ////////////////////////////////////////////////////////////////////////////////////////////////////////

        try {
            FileWriter fw = new FileWriter(logfile, true);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.newLine();
            bw.write("summary: ");
            bw.newLine();
            for (String key : breadSummaryMap.keySet()) {
                bw.write(key + " makes " + breadSummaryMap.get(key));
                bw.newLine();
            }
            for (String key : eggSummaryMap.keySet()) {
                bw.write(key + " makes " + eggSummaryMap.get(key));
                bw.newLine();
            }
            for (String key : sandwichSummaryMap.keySet()) {
                bw.write(key + " packs " + sandwichSummaryMap.get(key));
                bw.newLine();
            }
            bw.close();
        } catch (IOException e) {
            System.out.println("Main Thread IO Exception");
        }

    }
}



/********************************************************************************************************************************
 * buffer
 ********************************************************************************************************************************/

enum FoodType {
    EGG {
        public String getType() {
            return "egg";
        }
    },
    BREAD {
        public String getType() {
            return "bread";
        }
    };

    public abstract String getType();
}

class FoodItem {
    FoodType foodType;
    int foodId;
    String makerId;

    public FoodItem(FoodType foodType, int foodId, String makerId) {
        this.foodType = foodType;
        this.foodId = foodId;
        this.makerId = makerId;
    }

    public String getType() {
        return this.foodType.getType();
    }

    public int getFoodId() {
        return this.foodId;
    }

    public String getMakerId() {
        return this.makerId;
    }

    public String toString() {
        return getType() + " " + getFoodId() + " from " + getMakerId();
    }
}


class Buffer {
    private volatile FoodItem[] buffer;               // private / static volatile
    private volatile int front = -1, back = -1;         // private / static volatile
    private volatile int item_count = 0;              // public
    private volatile int size;
    private volatile int count = 0;

    Buffer(int size) {
        this.size = size;
        buffer = new FoodItem[size];
    }

    public synchronized void put(FoodItem foodItem) {

        while (item_count == size) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }
        if (front == -1)
            front = 0;
        back = (back + 1) % size;
        buffer[back] = foodItem;
        item_count++;
        count++;
        this.notify();
    }

    public synchronized FoodItem get() {
        while (item_count == 0) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }

        FoodItem foodItem = buffer[front];
        buffer[front] = null;

        if (front == back) {
            front = -1;
            back = -1;
        } else {
            front = (front + 1) % size;
        }
        item_count--;

        this.notify();

        return foodItem;
    }

}



/********************************************************************************************************************************
 * maker thread
 ********************************************************************************************************************************/

class MakeThread extends Thread {
    FoodType foodType;
    String makerid;
    Buffer buffer;
    BufferedWriter bw;
    int rate;
    int sandwiches;
    Map<String, Integer> breadSummaryMap;
    Map<String, Integer> eggSummaryMap;

    public MakeThread(FoodType foodType, String makerid, Buffer buffer, String fileName, int rate , int sandwiches, Map<String, Integer> breadSummaryMap, Map<String, Integer> eggSummaryMap) {
        this.foodType = foodType;
        this.makerid = makerid;
        this.buffer = buffer;
        this.rate = rate;
        this.sandwiches = sandwiches;
        this.breadSummaryMap = breadSummaryMap;
        this.eggSummaryMap = eggSummaryMap;

        try {
            FileWriter fw = new FileWriter(fileName, true);
            this.bw = new BufferedWriter(fw);
        } catch (Exception e) {
            System.out.println("Error while initialising filewriter");
        }

    }

    static void gowork(int n) {
        for (int i=0; i<n; i++){
            long m = 300000000;
            while (m>0){
                m--; 
            }
        }
    }

    @Override
    public void run() {
        switch (foodType) {
            case BREAD:
                Lock breadLock = new ReentrantLock();
                int breadid = 0;
                int breadCount = 0;

                while (true) {
                    breadLock.lock();
                    boolean notenough = false;

                    try {
                        if (breadCount < sandwiches * 2) {
                            breadCount++;
                            notenough = true;
                        } else {
                            notenough = false;
                            break;
                        }
                
                    } finally {
                        breadLock.unlock();
                        if (notenough) {
                            gowork(rate);
                            FoodItem bread = new FoodItem(FoodType.BREAD, breadid++, makerid);
                            buffer.put(bread);
                            try {
                                bw.write(productionEntry(bread));
                                bw.newLine();
                                bw.flush();
                            } catch (IOException e) {
                            }
                        }
                    }
                }

                if (breadSummaryMap.containsKey(makerid)) {
                    breadSummaryMap.put(makerid, breadSummaryMap.get(makerid) + 1);
                } else {
                    breadSummaryMap.put(makerid, 1);
                }

                break;

            case EGG:
                Lock eggLock = new ReentrantLock();

                int eggid = 0;
                int eggCount = 0;

                while (true) {
                    eggLock.lock();
                    boolean notenough = false;

                    try {
                        if (eggCount < sandwiches) {
                            eggCount++;
                            notenough = true;
                        } else {
                            notenough = false;
                            break;
                        }
                
                    } finally {
                        eggLock.unlock();
                        if (notenough) {
                            gowork(rate);
                            FoodItem egg = new FoodItem(FoodType.EGG, eggid++, makerid);
                            buffer.put(egg);
                            
                            try {
                                bw.write(productionEntry(egg));
                                bw.newLine();
                                bw.flush();
                            } catch (IOException e) {
                            }

                        }
                    }
                }

                if (eggSummaryMap.containsKey(makerid)) {
                    eggSummaryMap.put(makerid, eggSummaryMap.get(makerid) + 1);
                } else {
                    eggSummaryMap.put(makerid, 1);
                }

                break;

            default:
                try {
                    bw.close();
                } catch (IOException e) {
                    System.out.println("Maker error while closing buffered writer");
                }

                break;
        }
    }

    public String productionEntry(FoodItem foodItem) {
        StringBuilder sb = new StringBuilder();
        sb.append(makerid);
        sb.append(" puts ");
        sb.append(foodItem.getType());
        sb.append(foodItem.getFoodId());
        
        return sb.toString();
    }

}



/********************************************************************************************************************************
 * packer thread
 ********************************************************************************************************************************/

class PackerThread extends Thread {
    String serialNo;
    Buffer breadbuffer;
    Buffer eggbuffer;
    Object lock;
    int sandwichesToPack;
    int itemsPacked = 0;
    int rate;
    int sandwiches;
    Map<String, Integer> sandwichSummaryMap;
    private int sandwichid;
    private BufferedWriter bw;
    private FoodItem[] foodItems = new FoodItem[3];

    public PackerThread(String serialNo, Buffer breadbuffer, Buffer eggbuffer, String fileName, int rate, int sandwiches, Map<String, Integer> sandwichSummaryMap) {
        this.serialNo = serialNo;
        this.breadbuffer = breadbuffer;
        this.eggbuffer = eggbuffer;
        this.rate = rate;
        this.sandwiches = sandwiches;
        this.sandwichSummaryMap = sandwichSummaryMap;
        try {
            FileWriter fw = new FileWriter(fileName, true);
            this.bw = new BufferedWriter(fw);
        } catch (Exception e) {
            System.out.println("Error while initialising filewriter");
        }

    }

    static void gowork(int n) {
        for (int i=0; i<n; i++){
            long m = 300000000;
            while (m>0){
                m--; 
            }
        }
    }


    @Override
    public void run() {
        Lock sandwichLock = new ReentrantLock();
        int sandwichCount = 0;

        while (true) {
            sandwichLock.lock();
            boolean notenough = false;

            try {
                if (sandwichCount < sandwiches) {
                    sandwichCount++;
                    notenough = true;
                } else {
                    notenough = false;
                    break;
                }
        
            } finally {
                sandwichLock.unlock();
                if (notenough) {
                    foodItems[0] = breadbuffer.get();
                    foodItems[1] = eggbuffer.get();
                    foodItems[2] = breadbuffer.get();
                    sandwichid++;
                    gowork(rate);
                    try {
                        bw.write(packingEntry());
                        bw.newLine();
                        bw.flush();
                    } catch (IOException e) {
                    }
                }
            }
        }

        if (sandwichSummaryMap.containsKey(serialNo)) {
            sandwichSummaryMap.put(serialNo, sandwichSummaryMap.get(serialNo) + 1);
        } else {
            sandwichSummaryMap.put(serialNo, 1);
        }
    }

    public String packingEntry() {
        StringBuilder sb = new StringBuilder();
        sb.append(serialNo);
        sb.append(" packs sandwich " + sandwichid + " with ");       // sandwich count 
        sb.append(foodItems[0].getType() + " " + foodItems[0].getFoodId() + " from " + foodItems[0].getMakerId() + " and ");
        sb.append(foodItems[1].getType() + " " + foodItems[1].getFoodId() + " from " + foodItems[1].getMakerId() + " and ");
        sb.append(foodItems[2].getType() + " " + foodItems[2].getFoodId() + " from " + foodItems[2].getMakerId());

        return sb.toString();
    }

}


