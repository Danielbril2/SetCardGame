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

    private long sleepTime; // the time (in milliseconds) that the dealer need to sleep

    private Thread[] playerThreads;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        utilimpl = new UtilImpl(env.config);
        playerThreads = new Thread[env.config.players];
        sleepTime = 0;
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
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
//            removeCardsFromTable();
//            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        //need to also terminate all players
        for (Player p: players)
            p.terminate();
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
        // TODO now we need to check if a player has declared a set and remove the cards of the set
        // ללכת למערך במקום של הקלף ולהוריד מהמקום את הקלף גם במערך וגם בui


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
        // if there is no legal set on the table
        List<Integer> cards = new ArrayList<>();
        Collections.addAll(cards, table.slotToCard);
        boolean legalSetExists = utilimpl.findSets(cards, 1).size() > 0;
        if(!legalSetExists){
            removeAllCardsFromTable();
            placeCardsOnTable();
        }

        // notify all the players that they can return playing
        notifyAll();
    }

    private void shuffleCards(){
        Collections.shuffle(deck);
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
        if(reset){
            sleepTime = 1000;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        else if(reshuffleTime - System.currentTimeMillis() < 10000){
            sleepTime = 10;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
        }
        else
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable(){

        for (Thread p: playerThreads) { //do wait to all players
            try {p.wait();} //can be error?
            catch (InterruptedException ignored){}
        }
        env.ui.removeTokens();
        // adds the cards from the table to the deck and resets the arrays
        for(int i = 0; i < table.slotToCard.length; i++){
            deck.add(table.slotToCard[i]);
            table.removeCard(i);
        }
    }

    //should be synchorinzed, two players cannot access same function in the same time
    public void checkIfSet(int id, int[] cards){
        //or maybe add to action queue and just than implement to prevent locks?
        Player p = players[id];
        boolean isSet = utilimpl.testSet(cards);
        if(isSet){
            p.point();
            removeCardsFromTable(cards); // TODO remove the cards of the legal set from the table
            placeCardsOnTable();
        }
        else
            p.penalty();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Player> winnersList = new ArrayList<>();
        int maxScore = 0;
        for(Player p : players) {
            if (p.getScore() > maxScore)
                maxScore = p.getScore();
        }
        for(Player p : players) {
            if (p.getScore() == maxScore)
                winnersList.add(p);
        }
        int[] winners = new int[winnersList.size()];
        for(int i = 0; i < winners.length; i++)
            winners[i] = winnersList.remove(0).id;
        env.ui.announceWinner(winners);
        terminate();
    }

    private void CreatePlayersThreads(){
        String[] names = env.config.playerNames;
        for (int i = 0; i < players.length; i++) {
            Thread player;
            if (i < names.length)
                player = new Thread(players[i], names[i]);
            else {
                String name = "PLAYER " + Integer.toString(i);
                player = new Thread(players[i], name);
            }

            playerThreads[i] = player;
            player.start();
            try {player.wait();
            } catch (InterruptedException ignore) {
                env.logger.log(Level.WARNING,"player cannot wait until cards dealt");
            }
        }
        }
    }
