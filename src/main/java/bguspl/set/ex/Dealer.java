package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UtilImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private final UtilImpl utiliml;
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
    private long reshuffleTime = 60000; // one minute

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        utiliml = new UtilImpl(env.config);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");

        CreatePlayersThreads(); // creating players threads

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
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
    private void removeCardsFromTable() {
        // if there is no legal set on the table
        List<Integer> cards = new ArrayList<>();
        Collections.addAll(cards, table.slotToCard);
        boolean legalSetExists = utiliml.findSets(cards, 1).size() > 0;
        if(!legalSetExists){
            removeAllCardsFromTable();
            placeCardsOnTable();
        }
        // TODO now we need to check if a player has declared a set and remove the cards of the set
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        if (deck.size() >= env.config.tableSize - table.countCards()) {
            if (table.countCards() < env.config.tableSize) {
                for (int i = 0; i < table.slotToCard.length; i++) {
                    if (table.slotToCard[i] == null)
                        table.placeCard(deck.remove(0), i);
                }
            }
        }
        // notify all the players
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // adds the cards from the table to the deck and resets the arrays
        for(int i = 0; i < table.slotToCard.length; i++){
            deck.add(table.slotToCard[i]);
            table.removeCard(i);
        }
        // do wait for the players
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Player> winners = new ArrayList<>();
        int maxScore = 0;
        for(Player p : players) {
            if (p.getScore() > maxScore)
                maxScore = p.getScore();
        }
        for(Player p : players) {
            if (p.getScore() == maxScore)
                winners.add(p);
        }
        // TODO display the winner
    }

    private void CreatePlayersThreads()
    {
        String[] names = env.config.playerNames;
        for (int i = 0; i < players.length; i++)
        {
            Thread player = new Thread(players[i],names[i]);
            player.start();
        }
    }
}
