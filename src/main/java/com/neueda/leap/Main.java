package com.neueda.leap;

public class Main {
    public static void main(String[] args) {
        // TODO: replace jeepers-leapers? with your team's actual name
        System.out.println("This is the Jeepers Leapers application!");
        System.out.println("Container is up and running.");
        System.out.println("Sleeping...");
        System.out.println("Inspect container and check docker-compose networking...");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        while(true){
            System.out.println("Periodic check...");
            try {
                Thread.sleep(10000); 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
