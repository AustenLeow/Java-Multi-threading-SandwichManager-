import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/********************************************************************************************************************************
 * main thread
 ********************************************************************************************************************************/

public class SandwichManager {
    static Object lock = new Object();
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
            bw.write("sandwiches:" + sandwiches);
            bw.newLine();
            bw.write("bread capacity:" + breadcap);
            bw.newLine();
            bw.write("egg capacity:" + eggcap);
            bw.newLine();
            bw.write("bread makers:" + breadmakers);
            bw.newLine();
            bw.write("egg makers:" + eggmakers);
            bw.newLine();
            bw.write("sandwich packers:" + packers);
            bw.newLine();
            bw.write("bread rate:" + breadrate);
            bw.newLine();
            bw.write("egg rate:" + eggrate);
            bw.newLine();
            bw.write("packing rate:" + packingrate);
            bw.newLine();
            bw.newLine();
            bw.flush();
            bw.close();
        } catch (IOException e) {
            System.out.println("invalid input");
        }

        Buffer breadbuffer = new Buffer(breadcap);
        Buffer eggbuffer = new Buffer(eggcap);


        List<Thread> threads = new ArrayList<Thread>();

        // bread makers
        int breadStart = 0;
        int numBreadEachMaker = (int) Math.ceil((double) sandwiches*2 / breadmakers);
        for (int i = 0; i < breadmakers; i++) {
            String makerid = "B" + i;
            int end = Math.min(breadStart + numBreadEachMaker, sandwiches*2);
            MakeThread breadMaker = new MakeThread(FoodType.BREAD, makerid, breadStart, end, lock, breadbuffer, logfile, breadrate);
            breadStart = end;
            threads.add(breadMaker);
        }

        // egg makers
        int eggStart = 0;
        int numEggEachMaker = (int) Math.ceil((double) sandwiches / eggmakers);
        for (int i = 0; i < eggmakers; i++) {
            String makerid = "E" + i;
            int end = Math.min(eggStart + numEggEachMaker, sandwiches);
            MakeThread eggMaker = new MakeThread(FoodType.EGG, makerid, eggStart, end, lock, eggbuffer, logfile, eggrate);
            eggStart = end;
            threads.add(eggMaker);
        }

        // packers
        int numBreadEachPacker = (int) Math.ceil((double) sandwiches / packers);
        if (numBreadEachPacker % 2 == 1)
            numBreadEachPacker++;

        for (int i = 0; i < packers; i++) {
            String packerid = "S" + i;
            int toPack = Math.min(numBreadEachPacker, sandwiches);
            PackerThread packer = new PackerThread(packerid, toPack, lock, breadbuffer, eggbuffer, logfile, packingrate);
            sandwiches -= toPack;
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

        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(logfile));
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        }

        // Skip first 10 lines
        for (int i = 0; i < 10 ; i++) {
            scanner.nextLine();
        }

        Map<String, Integer> breadMakerMap = new HashMap<>();
        Map<String, Integer> eggMakerMap = new HashMap<>();
        Map<String, Integer> packerMap = new HashMap<>();
        while (scanner.hasNext()) {
            String line = scanner.nextLine();
            String[] logEntry = line.split(" ");

            String actor = logEntry[0];
            String command = logEntry[1];
            String foodType = logEntry[2];

            if (command.equals("puts")) {
                if (foodType.contains("bread")) {
                    if (breadMakerMap.containsKey(actor)) {
                        breadMakerMap.put(actor, breadMakerMap.get(actor) + 1);
                    } else {
                        breadMakerMap.put(actor, 1);
                    }
                } else if (foodType.contains("egg")) {
                    if (eggMakerMap.containsKey(actor)) {
                        eggMakerMap.put(actor, eggMakerMap.get(actor) + 1);
                    } else {
                        eggMakerMap.put(actor, 1);
                    }
                }
            }

            if (command.equals("packs")) {
                if (packerMap.containsKey(actor)) {
                    packerMap.put(actor, packerMap.get(actor) + 1);
                } else {
                    packerMap.put(actor, 1);
                }
            }
        }

        try {
            FileWriter fw = new FileWriter(logfile, true);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.newLine();
            bw.write("summary: ");
            bw.newLine();
            for (String key : breadMakerMap.keySet()) {
                bw.write(key + " makes " + breadMakerMap.get(key));
                bw.newLine();
            }
            for (String key : eggMakerMap.keySet()) {
                bw.write(key + " makes " + eggMakerMap.get(key));
                bw.newLine();
            }
            for (String key : packerMap.keySet()) {
                bw.write(key + " packs " + packerMap.get(key));
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
    static volatile FoodItem[] buffer;               // private / static volatile
    static volatile int front = 0, back = 0;         // private / static volatile
    static volatile int item_count = 0;              // public

    Buffer(int size) {
        buffer = new FoodItem[size];
    }

    public synchronized void put(FoodItem foodItem) {

        while (item_count == buffer.length) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }
        buffer[back] = foodItem;
        back = (back + 1) % buffer.length;
        item_count++;
        this.notifyAll();
    }

    public synchronized FoodItem get() {
        while (item_count == 0) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }

        FoodItem foodItem = buffer[front];
        front = (front + 1) % buffer.length;
        item_count--;
        this.notifyAll();

        return foodItem;
    }

}



/********************************************************************************************************************************
 * maker thread
 ********************************************************************************************************************************/

class MakeThread extends Thread {
    FoodType foodType;
    String serialNo;
    int start, end;
    private Object lock;
    Buffer buffer;
    BufferedWriter bw;
    int rate;

    public MakeThread(FoodType foodType, String serialNo, int start, int end, Object lock, Buffer buffer, String fileName, int rate) {
        this.foodType = foodType;
        this.serialNo = serialNo;
        this.start = start;
        this.end = end;
        this.lock = lock;
        this.buffer = buffer;
        this.rate = rate;

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
                while (start < end) {
                    try {
                        FoodItem bread = new FoodItem(FoodType.BREAD, start, serialNo);
                        start++;
                        // Thread.sleep(3000);
                        gowork(rate);
                        synchronized (lock) {
                            bw.write(productionEntry(bread));
                            bw.newLine();
                            bw.flush();
                        }
                        buffer.put(bread);
                        // Thread.sleep(1000);
                        

                    // } catch (InterruptedException ie) {
                    //     System.out.println("Error while putting thread to sleep");
                    } catch (IOException io) {
                        System.out.println("Error while writing to summary file");
                    }
                }

                break;
            case EGG:
                while (start < end) {

                    try {
                        FoodItem egg = new FoodItem(FoodType.EGG, start, serialNo);
                        start++;
                        // Thread.sleep(8000);
                        gowork(rate);
                        synchronized (lock) {
                            bw.write(productionEntry(egg));
                            bw.newLine();
                            bw.flush();
                        }
                        buffer.put(egg);
                        
                        // Thread.sleep(1000);
                    // } catch (InterruptedException e) {
                    //     System.out.println("Maker Error while putting thread to sleep");
                    } catch (IOException io) {
                        System.out.println("Maker Error while writing to summary file");
                    }

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
        sb.append(serialNo);
        sb.append(" puts ");
        sb.append(foodItem.getType());
        sb.append(foodItem.getFoodId());
        
        return sb.toString();
    }

    // @Override
    // public String toString() {
    //     return "Maker with id " + serialNo + " producing " + foodType.getType() + " from start: " + start
    //             + " to end: " + end;
    // }
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
    private BufferedWriter bw;
    private FoodItem[] foodItems = new FoodItem[3];

    public PackerThread(String serialNo, int sandwichesToPack, Object lock, Buffer breadbuffer, Buffer eggbuffer, String fileName, int rate) {
        this.serialNo = serialNo;
        this.sandwichesToPack = sandwichesToPack;
        this.breadbuffer = breadbuffer;
        this.eggbuffer = eggbuffer;
        this.lock = lock;
        this.rate = rate;
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
        while (itemsPacked < sandwichesToPack) {
            try {
                // Thread.sleep(1000);
                foodItems[0] = breadbuffer.get();
                // Thread.sleep(2000);
                // this.setPriority(Thread.MAX_PRIORITY);


                // Thread.sleep(1000);
                foodItems[1] = eggbuffer.get();
                // Thread.sleep(2000);


                // Thread.sleep(1000);
                foodItems[2] = breadbuffer.get();
                // Thread.sleep(2000);

               
                
                itemsPacked++;

                gowork(rate);

                synchronized (lock) {
                    bw.write(packingEntry());
                    bw.newLine();
                    bw.flush();
                }

                // this.setPriority(Thread.NORM_PRIORITY);

            // } catch (InterruptedException ie) {
            //     System.out.println("Packer interrupted while packing items");
            } catch (IOException io) {
                System.out.println("Packer error while closing buffered writer");
            }
        }
    }

    public String packingEntry() {
        StringBuilder sb = new StringBuilder();
        sb.append(serialNo);
        sb.append(" packs sandwich " + "" + " with ");       // sandwich count 
        sb.append(foodItems[0].getType() + " " + foodItems[0].getFoodId() + " from " + foodItems[0].getMakerId() + " and ");
        sb.append(foodItems[1].getType() + " " + foodItems[1].getFoodId() + " from " + foodItems[1].getMakerId() + " and ");
        sb.append(foodItems[2].getType() + " " + foodItems[2].getFoodId() + " from " + foodItems[2].getMakerId());

        return sb.toString();
    }

    // @Override
    // public String toString() {
    //     return "Packer with id " + serialNo + " packing " + itemsToPack + " " + foodType.getType();
    // }
}


