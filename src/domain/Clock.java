package domain;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ThreadLocalRandom;

public class Clock extends Thread{
    private Integer delay;
    private LocalTime time;
    public Clock(int delay, LocalTime time){
        this.delay = delay;
        this.time = time;
    }

    public LocalTime getTime() {
        return time;
    }

    public long timeOnMs() {
        return time.toSecondOfDay() * 1000L + time.getNano() / 1000;
    }

    public void increase(long ms){
        time = time.plus(ms, ChronoUnit.MILLIS);
    }

    public void decrease(long ms){
        time = time.minus(ms, ChronoUnit.MILLIS);
    }

    @Override
    public void run() {
        while (true){
            try {
                Thread.sleep(1000);
                int randomNum = ThreadLocalRandom.current().nextInt(1, 10 + 1);
                if (randomNum/3==0)Thread.sleep((long) delay);
                increase(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
