package bguspl.set.ex;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.Random;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private Queue<Integer> actionQueue;
    private Dealer dealer;
    private Semaphore sem;
    private Object waitForCards;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.actionQueue = new LinkedList<>();
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        try {
            synchronized (waitForCards) {
                waitForCards.wait(); //waiting in the beginning until cards are dealt
            }
                env.logger.log(Level.INFO, Thread.currentThread().getName() + " waiting ");
        }catch (Exception ingored) {
            env.logger.log(Level.WARNING,ingored.toString());
        }

        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if (actionQueue.size() > 0)
            {
                int action = actionQueue.poll();
                //implement action
                table.makeAction(id,action);
                //ask table if we have 3 tokens
                boolean hasSet = table.isCheck(id);
                if (hasSet) {
                    int[] cards = table.getPlayerCards(id);
                    try { //manages that only one player can go to the dealer each time
                        sem.acquire();
                        dealer.checkIfSet(id, cards);
                    }
                    catch (InterruptedException ignored) {}
                    sem.release();
                }
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");

            while (!terminate) {
                //need to generate random number between 0-11
                Random rand = new Random();
                int slot = rand.nextInt(12);
                keyPressed(slot);

                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }

            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (actionQueue.size() < 3)
            actionQueue.add(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        long startTime = System.currentTimeMillis();
        long remainTime = System.currentTimeMillis() - startTime;
        while (remainTime < env.config.pointFreezeMillis)
        {
            env.ui.setFreeze(this.id,remainTime);
            remainTime = System.currentTimeMillis() - startTime;
        }


        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        //env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long startTime = System.currentTimeMillis();
        long remainTime = System.currentTimeMillis() - startTime;
        //we are in a loop for a period of time, so we cannot do anything
        //not sure if it works, maybe need to sleep for some time
        while (remainTime < env.config.penaltyFreezeMillis)
        {
            env.ui.setFreeze(this.id,remainTime);
            remainTime = System.currentTimeMillis() - startTime;
        }
    }

    public int getScore() {
        return score;
    }

    public void setSemaphore(Semaphore sem)
    {
        this.sem = sem;
    }

    public void setLockObject(Object obj)
    {
        this.waitForCards = obj;
    }
}
