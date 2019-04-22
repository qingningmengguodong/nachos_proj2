package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
	private int temp; 
    private	Condition speaker;
    private Condition listener;
    private Condition finish;
    private Lock lock;
    boolean newMessage;
	
    public Communicator() {
    	temp = 0;
    	speaker = new Condition(lock);
    	listener = new Condition(lock);
    	finish = new Condition(lock);
    	lock = new Lock();
    	newMessage = false;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();
        
        while (newMessage)
        	speaker.sleep();
        temp = word;
        newMessage = true;
        listener.wake();
        finish.sleep();
        
        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	int t;
    	lock.acquire();
    	
    	while (!newMessage)
    		listener.sleep();
    	t = temp;
    	newMessage = false;
    	finish.wake();
    	speaker.wake();
    	
    	lock.release();
    	return t;
    }
}
