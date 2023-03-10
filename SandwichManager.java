import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/******************************************************************************************************************************************************************
 * main thread
 ******************************************************************************************************************************************************************/

public class SandwichManager {

    static String logfile = "log.txt";

    // keep track of <maker/packer id, highest id it has made/packed> for summary
    static final Map<String, Integer> breadSummaryMap = new HashMap<String, Integer>();
    static final Map<String, Integer> eggSummaryMap = new HashMap<String, Integer>();
    static final  Map<String, Integer> sandwichSummaryMap = new HashMap<String, Integer>();

    public static void main(String[] args) {

        // commmand line arguments
        int sandwiches = Integer.parseInt(args[0]);
        int breadcap = Integer.parseInt(args[1]);
        int eggcap = Integer.parseInt(args[2]);
        int breadmakers = Integer.parseInt(args[3]);
        int eggmakers = Integer.parseInt(args[4]);
        int packers = Integer.parseInt(args[5]);
        int breadrate = Integer.parseInt(args[6]);
        int eggrate = Integer.parseInt(args[7]);
        int packingrate = Integer.parseInt(args[8]);

        // first 10 lines of log.txt to show user input
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

        // bread and egg pools
        Buffer breadbuffer = new Buffer(breadcap);
        Buffer eggbuffer = new Buffer(eggcap);

        // keep track of all the worker threads
        List<Thread> threads = new ArrayList<Thread>();

        // bread maker threads
        for (int i = 0; i < breadmakers; i++) {
            String makerid = "B" + i;
            MakeThread breadMaker = new MakeThread(FoodType.BREAD, makerid, breadbuffer, logfile, breadrate, sandwiches);
            threads.add(breadMaker);
        }

        // egg maker threads
        for (int i = 0; i < eggmakers; i++) {
            String makerid = "E" + i;
            MakeThread eggMaker = new MakeThread(FoodType.EGG, makerid, eggbuffer, logfile, eggrate, sandwiches);
            threads.add(eggMaker);
        }

        // packer threads
        for (int i = 0; i < packers; i++) {
            String packerid = "S" + i;
            PackerThread packer = new PackerThread(packerid, breadbuffer, eggbuffer, logfile, packingrate, sandwiches);
            threads.add(packer);
        }

        // run worker threads in the list of threads
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


        //////////////////  count summary  ////////////////////////////////////////////////////////////////
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



/******************************************************************************************************************************************************************
 * bread / egg / buffer
 ******************************************************************************************************************************************************************/

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

// buffer to behave like a circular queue
class Buffer {
    private volatile FoodItem[] buffer;               
    private volatile int front = -1, back = -1;       // initialisation of front and back of circular queue
    private volatile int item_count = 0;              
    private volatile int size;

    Buffer(int size) {
        this.size = size;
        buffer = new FoodItem[size];
    }

    // enqueue
    public synchronized void put(FoodItem foodItem) {
        
        // full queue
        while (item_count == size) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }

        // empty queue
        if (front == -1)
            front = 0;

        back = (back + 1) % size;
        buffer[back] = foodItem;
        item_count++;
        this.notify();
    }

    // dequeue
    public synchronized FoodItem get() {
        
        // empty queue
        while (item_count == 0) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }

        FoodItem foodItem = buffer[front];
        buffer[front] = null;

        // only 1 element in queue
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



/******************************************************************************************************************************************************************
 * bread/egg maker thread
 ******************************************************************************************************************************************************************/

class MakeThread extends Thread {
    FoodType foodType;
    String makerid;
    Buffer buffer;
    BufferedWriter bw;
    int rate;
    int sandwiches;
    
    // all threads to see the same bread/egg counts so can maintain <= required amount based on number of sandwiches
    volatile static int breadCount = 0;        
    volatile static int eggCount = 0;

    public MakeThread(FoodType foodType, String makerid, Buffer buffer, String fileName, int rate , int sandwiches) {
        this.foodType = foodType;
        this.makerid = makerid;
        this.buffer = buffer;
        this.rate = rate;
        this.sandwiches = sandwiches;

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
        // try {
        //     Thread.sleep(n);
        // } catch (InterruptedException e) {
        //     System.out.println("got interrupted!");
        // }
    }

    @Override
    public void run() {
        switch (foodType) {
            case BREAD:
                Lock breadLock = new ReentrantLock();

                // to keep track how many bread each specific thread made
                int breadid = 0;

                while (true) {

                    gowork(rate);

                    // lock the part when we are keeping track of food count, so the the update happens 1 by 1
                    breadLock.lock();

                    try {
                        if (breadCount >= sandwiches * 2) { // check if enough bread has been made
                            break; // exit while loop if enough bread has been made
                        }

                        FoodItem bread = new FoodItem(FoodType.BREAD, breadid++, makerid);

                        // add to pool
                        buffer.put(bread);
                        try {
                            // call print function
                            bw.write(productionEntry(bread));
                            bw.newLine();
                            bw.flush();
                        } catch (IOException e) {
                        }

                        breadCount++;
                    
                    } finally {
                        breadLock.unlock();
                    }

                }

                // update hashmap 
                SandwichManager.breadSummaryMap.put(makerid, breadid);
                
                break;
            
            // similar logic as bread
            case EGG:
                Lock eggLock = new ReentrantLock();

                int eggid = 0;

                while (true) {
                    gowork(rate);

                    // lock the part when we are keeping track of food count, so the the update happens 1 by 1
                    eggLock.lock();

                    try {
                        if (eggCount >= sandwiches) { 
                            break; 
                        }

                        FoodItem egg = new FoodItem(FoodType.EGG, eggid++, makerid);

                        // add to pool
                        buffer.put(egg);
                        try {
                            bw.write(productionEntry(egg));
                            bw.newLine();
                            bw.flush();
                        } catch (IOException e) {
                        }

                        eggCount++;

                    } finally {
                        eggLock.unlock();
                    }
                }

                SandwichManager.eggSummaryMap.put(makerid, eggid);

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

    // print function after adding to pool
    public String productionEntry(FoodItem foodItem) {
        StringBuilder sb = new StringBuilder();
        sb.append(makerid);
        sb.append(" puts ");
        sb.append(foodItem.getType());
        sb.append(" ");
        sb.append(foodItem.getFoodId());
        
        return sb.toString();
    }

}



/******************************************************************************************************************************************************************
 * sadnwich packer thread
 ******************************************************************************************************************************************************************/

class PackerThread extends Thread {
    String serialNo;
    Buffer breadbuffer;
    Buffer eggbuffer;
    Object lock;
    int sandwichesToPack;
    int itemsPacked = 0;
    int rate;
    int sandwiches;

    // all threads to see the same sandwich count so can maintain <= required amount based on number of sandwiches
    volatile static int sandwichCount = 0;

    // to keep track how many sandwich each specific thread packed
    private int sandwichid = 0;

    private BufferedWriter bw;
    private FoodItem[] foodItems = new FoodItem[3];

    public PackerThread(String serialNo, Buffer breadbuffer, Buffer eggbuffer, String fileName, int rate, int sandwiches) {
        this.serialNo = serialNo;
        this.breadbuffer = breadbuffer;
        this.eggbuffer = eggbuffer;
        this.rate = rate;
        this.sandwiches = sandwiches;

        try {
            FileWriter fw = new FileWriter(fileName, true);
            this.bw = new BufferedWriter(fw);
        } catch (Exception e) {
            System.out.println("Error while initialising filewriter");
        }

    }

    static void gowork(int n) {
        // for (int i=0; i<n; i++){
        //     long m = 300000000;
        //     while (m>0){
        //         m--; 
        //     }
        // }
        try {
            Thread.sleep(n);
        } catch (InterruptedException e) {
            System.out.println("got interrupted!");
        }
    }


    @Override
    public void run() {
        Lock sandwichLock = new ReentrantLock();

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

                    // take ingredients from respective pools
                    foodItems[0] = breadbuffer.get();
                    foodItems[1] = eggbuffer.get();
                    foodItems[2] = breadbuffer.get();
                    
                    gowork(rate);

                    try {

                        // call print function
                        bw.write(packingEntry());
                        bw.newLine();
                        bw.flush();
                    } catch (IOException e) {
                    }

                    sandwichid++;
                }
            }
        }

        // update sandwich map
        SandwichManager.sandwichSummaryMap.put(serialNo, sandwichid);
    }

    // print function after packing sandwich
    public String packingEntry() {
        StringBuilder sb = new StringBuilder();
        sb.append(serialNo);
        sb.append(" packs sandwich " + sandwichid + " with ");     
        sb.append(foodItems[0].getType() + " " + foodItems[0].getFoodId() + " from " + foodItems[0].getMakerId() + " and ");
        sb.append(foodItems[1].getType() + " " + foodItems[1].getFoodId() + " from " + foodItems[1].getMakerId() + " and ");
        sb.append(foodItems[2].getType() + " " + foodItems[2].getFoodId() + " from " + foodItems[2].getMakerId());

        return sb.toString();
    }

}


