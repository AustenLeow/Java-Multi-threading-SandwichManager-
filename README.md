# Java-Multi-threading-SandwichManager-
1. To compile:
```
javac SandwichManager.java
```

2. Run with sample arguments: 
sandwiches:10
bread capacity:4
egg capacity:4
bread makers:3
egg makers:3
sandwich packers:3
bread rate:3
egg rate:5
packing rate:4
```
java SandwichManager 10 4 4 3 3 3 3 5 4
```

3. Open log.txt to see the records


## Problem
#### Introduction
Consider a sandwich making factory with three types of machines: a bread toaster, a scrambled egg maker, and a sandwich packer
- A bread toaster will toast a single slice of bread and put it in a bread pool
- A scrambled egg maker will make a single portion of scrambled egg and put it in the
scrambled egg pool
- A sandwich packer takes one slice of bread from bread pool, then one portion of
scrambled egg from the scrambled egg pool, and finally take one more slice of bread from the bread pool, and then pack the sandwich.

The factory has multiple bread toasters, multiple scrambled egg makers and multiple sandwich packers working concurrently. There is also one shared bread pool and one shared scrambled egg pool.

The manager of the factory specifies the number of sandwiches to pack. There is a log file containing records for logging when a bread slice is made, a portion of scrambled egg is made and a sandwich is packed. When all the sandwiches are packed, the manager will append a summary to the log file.

#### Requirements
Design and implement a multithreaded Java program that works as follows:

- Each making machine, i.e. bread toaster or scrambled egg maker, is modeled with a
thread
  - The making machine makes one food item at a time
  - The making machine either takes bread_rate minutes to toast a slice of bread, or egg_rate minutes to make a portion of scrambled egg
  - After an item is made, it is immediately served to a common pool. The slices of bread go to the bread pool, the scrambled egg go to the egg pool
  - After making the food, the making machine writes a record to the log file in the form of “<id> puts <type> <i>”, where id is a serial number of the making machine, type is either “bread” or “egg”, i is serial number of the food
  - Once a making machine finishes sending the food to the pool, it can start to make the next food item
  - When the required number of breads or scrambled egg are made, the making machine stops. Note that a single sandwich requires two slices of bread and one portion of scrambled egg

- Each sandwich packer is modeled with a thread.
  - A sandwich packer first takes a slice of bread from the bread pool. If no bread is available the packer is blocked until bread pool is non-empty
  - It then gets a portion of scrambled egg from the egg pool. If no egg is available, the packer is blocked until egg pool is non-empty
  - Finally it get a slice of bread from the bread pool. Again it will block until bread pool is non-empty
  - After taking the food from the pool, the packing machine takes packing_rate minutes to pack the sandwich
  - After packing, it writes a record of the form “<id0> packs sandwich <i> with bread <j> from <id1> and egg <k> from <id2> and bread <m> from <id3>” where
    - i is the sandwich serial number, id0 is the packer id
    - j is the bread serial number for the top slice made by maker <id1>
    - k is the egg serial number made by maker <id2>
    - m is the bread serial number for the bottom slice made by maker <id3> o Once the packing machine finishes writing the record, it can start packing the next item

- The manager is the main thread that manages the production and packing
  - Themanagerinitiatesbreadtoastingmachines,scrambledeggmakingmachines and sandwich packers
  - Themanagerhastowaituntilfoodaremadeandpacked,aswellasallrecords are logged
  - After all machines stop, the manager appends a summary to the log file. The summary provides statistics about the number of bread and scrambled eggs made and sandwiches packed by each machine

- There are two common pools: one for bread, one for egg. Each common pool is a circular queue
  - Thequeuehaseitherbread_capacityslotsoregg_capacityslots
  - An arrival food, either a bread or a portion of scrambled, is inserted to the end of the queue
  - The food items are picked in first-come-first-serve manner
  - The pool will refuse accepting new arrival food when there is no empty slot o The pool will not deliver food to packing machine when all slots are empty
