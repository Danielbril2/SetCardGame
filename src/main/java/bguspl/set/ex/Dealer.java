package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UtilImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    private final UtilImpl utilimpl;
    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Integer.MAX_VALUE; // will change before the time loop

    private long sleepTime = 100; // the time (in milliseconds) that the dealer need to sleep

    private final Semaphore sem;
    private long lastUpdate; //the last time we updated the time
    private final Object waitForCards;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        utilimpl = new UtilImpl(env.config);
        sem = new Semaphore(1); //we only want one player to access dealer each time
        lastUpdate = 0;
        waitForCards = new Object();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");

        CreatePlayersThreads(); // creating players threads

        shuffleCards();

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop(); //for one minute
            updateTimerDisplay(true); //reset after one minute
            removeAllCardsFromTable();
            shuffleCards();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout(); //basically sleep for a second
            updateTimerDisplay(false); //need to change function - check if we need to update seconds
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        //need to also terminate all players
        for (Player p : players)
            p.terminate();
        Thread.currentThread().interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(int[] cards) {
        for (Player p: players) { //do wait to all players
            p.setIsCardDealt(false);
            p.PlayerWait();
        }

        for (int cardId : cards) { // remove the cards of the set from the table and the deck
            int slot = table.cardToSlot[cardId];
            table.removeCard(slot);
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        if (table.countCards() < env.config.tableSize &&
                deck.size() >= env.config.tableSize - table.countCards()) {
            for (int i = 0; i < table.slotToCard.length; i++) {
                if (table.slotToCard[i] == null)
                    table.placeCard(deck.remove(0), i);
            }
        }
        // if there is no legal set on the table
        List<Integer> cards = new ArrayList<>();
        Collections.addAll(cards, table.slotToCard);
        boolean legalSetExists = utilimpl.findSets(cards, 1).size() > 0;
        if (!legalSetExists) {
            removeAllCardsFromTable();
            placeCardsOnTable();
        }

        // notify all the players that they can return playing
        for (Player p: players)
            p.setIsCardDealt(true);
        try {
            synchronized (waitForCards) {waitForCards.notifyAll();}
        }
        catch (Exception ignored) {}
    }

    private void shuffleCards() {
        Collections.shuffle(deck);
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        } else {
            //check if a second has passed since last update, if yes that update countdown
            //else, change sleepTime to the difference
            int SECOND = 1000;
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            boolean isRed = timeLeft < env.config.turnTimeoutWarningMillis;
            env.ui.setCountdown(timeLeft, isRed);
            sleepTime = SECOND / 100;
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (Player p : players) { //all players should wait while there are no cards
            p.setIsCardDealt(false);
            p.PlayerWait();
        }
        env.ui.removeTokens();
        // adds the cards from the table to the deck and resets the arrays
        for (int i = 0; i < table.slotToCard.length; i++) {
            deck.add(table.slotToCard[i]);
            table.removeCard(i);
        }
    }

    public void checkIfSet(int playerId, int[] cards) {
        Player p = players[playerId];
        boolean isSet = utilimpl.testSet(cards);

        if (isSet) {
            p.point();
            removeCardsFromTable(cards); //need to also update the tokens
            placeCardsOnTable();
        } else
            p.penalty();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Player> winnersList = new ArrayList<>();
        int maxScore = 0;
        for (Player p : players) {
            if (p.getScore() > maxScore)
                maxScore = p.getScore();
        }
        for (Player p : players) {
            if (p.getScore() == maxScore)
                winnersList.add(p);
        }
        int[] winners = new int[winnersList.size()];
        for (int i = 0; i < winners.length; i++)
            winners[i] = winnersList.remove(0).id;
        env.ui.announceWinner(winners);
        terminate();
    }

    private void CreatePlayersThreads() {
        String[] names = env.config.playerNames;
        for (Player p : players)
        {
            p.setSemaphore(this.sem); //giving each player the same semaphore
            p.setLockObject(this.waitForCards); //giving each player the same lock object
        }

        for (int i = 0; i < players.length; i++) {
            Thread player;
            if (i < names.length)
                player = new Thread(players[i], names[i]);
            else {
                String name = "PLAYER " + i;
                player = new Thread(players[i], name);
                env.logger.log(Level.INFO,name + " starting");
            }

            player.start();
        }
    }
}
