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
    private boolean isCardDealt;

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
        isCardDealt = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        PlayerWait(); //waiting until all cards are dealt

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
        Thread.currentThread().interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (actionQueue.size() < 3)
            actionQueue.add(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() { //need to add that sleep only for a second
        env.ui.setScore(id, ++score);
        long freezeTime = env.config.penaltyFreezeMillis;
        long updateTime = 1000; //second
        env.ui.setFreeze(this.id, freezeTime);
        while (freezeTime > 0) {
            try {Thread.sleep(updateTime);
            } catch (InterruptedException ignored) {}
            freezeTime -= updateTime;
            env.ui.setFreeze(this.id,freezeTime);
        }
        env.ui.setFreeze(this.id, 0);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long freezeTime = env.config.penaltyFreezeMillis;
        long updateTime = 1000; //second
        env.ui.setFreeze(this.id, freezeTime);
        while (freezeTime > 0) {
            try {Thread.sleep(updateTime);
            } catch (InterruptedException ignored) {}
            freezeTime -= updateTime;
            env.ui.setFreeze(this.id,freezeTime);
        }
        env.ui.setFreeze(this.id, 0);
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

    public void setIsCardDealt(boolean isCardDealt)
    {
        this.isCardDealt = isCardDealt;
    }

    public void PlayerWait() {
        try {
            while (isCardDealt) {
                synchronized (waitForCards) {
                    waitForCards.wait(); //waiting in the beginning until cards are dealt
                }
            }
        } catch (Exception ingored) {
        }
    }
}
