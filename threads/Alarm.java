package nachos.threads;

import nachos.machine.*;

import java.util.PriorityQueue;
/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
	//KThread.currentThread().yield();
    	boolean intStatus = Machine.interrupt().disable();
    	waitUntilTimer first = sleeping.peek();
    	
    	while (first != null && first.getwaitTime() <= Machine.timer().getTime()) {
    		sleeping.remove().getThread().ready();
    		first = sleeping.peek();
    	}
    	
    	KThread.yield();
    	Machine.interrupt().restore(intStatus);
    }
    
    public class waitUntilTimer implements Comparable<waitUntilTimer> {
    	private KThread thread;
    	private long waitTime;
    	
    	public waitUntilTimer (KThread thread_, long waitTime_) {
    		thread = thread_;
    		waitTime = waitTime_;
    	}
    	
    	public long getwaitTime() {
    		return waitTime;
    	}
    	
    	public KThread getThread() {
    		return thread;
    	}
    	
    	@Override
    	public int compareTo(waitUntilTimer another) {
    		return (int) (this.waitTime - another.waitTime);
    	}
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	// for now, cheat just to get something working (busy waiting is bad)
	long wakeTime = Machine.timer().getTime() + x;
	waitUntilTimer current = new waitUntilTimer(KThread.currentThread(),wakeTime);
	//while (wakeTime > Machine.timer().getTime())
	//   KThread.yield();
    
	
	boolean intStatus = Machine.interrupt().disable();
	sleeping.add(current);
	KThread.sleep();
	Machine.interrupt().restore(intStatus);
    }
    
    private PriorityQueue<waitUntilTimer> sleeping = new PriorityQueue<waitUntilTimer>();
}
