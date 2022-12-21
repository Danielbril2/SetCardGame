package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;
/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    private Integer[][] tokens; //tokens[playerId][slot1,slot2, slot3]
    private Integer[] numOfTokens; //represents how many placed tokens each player have

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        initializeData();
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        // TODO implement
        env.ui.placeCard(card,slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}


        int card = slotToCard[slot];
        //first delete the tokens
        for (int i = 0; i < tokens.length;i++) //for each player
        {
            int playerTokens = numOfTokens[i];
            for (int j = 0; j < playerTokens; j++) //check all his tokens
            {
                if (tokens[i][j] == slot) //if he has a token that will be deleted
                    removeToken(i,slot);
            }
        }

        slotToCard[slot] = null;
        cardToSlot[card] = null;
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        int tokenOfPlayer = numOfTokens[player];
        if (tokenOfPlayer < 3) //add slot to token
        {
            tokens[player][tokenOfPlayer] = slot;
            numOfTokens[player]++;

            env.ui.placeToken(player,slot);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        Integer[] playersAction = tokens[player];
        int tokenOfPlayer = numOfTokens[player];
        boolean isRemoved = false;
        for (int i = 0; i < tokenOfPlayer - 1; i++)
        {
            if (!isRemoved) {
                if (playersAction[i] == slot) //here should delete the slot
                {
                    playersAction[i] = playersAction[i + 1];
                    isRemoved = true;
                }
            }
            else{
                playersAction[i] = playersAction[i + 1];
            }
        }

        playersAction[tokenOfPlayer - 1] = -1; // not really necessary but to be sure
        numOfTokens[player]--;
        tokens[player] = playersAction;
        env.ui.removeToken(player,slot);
        return true;
    }

    // checks if we placed token, if so then removes, else, puts the token
    public void makeAction(int player, int slot) {
        Integer[] playerTokens = this.tokens[player];
        for (Integer playerToken : playerTokens)
            if (playerToken == slot) { // remove token
                removeToken(player, slot);
                return;
            }
        //add token
        placeToken(player, slot);
    }

    public boolean isCheck(int player)
    {
        return numOfTokens[player] == 3; //returns true if we have 3 tokens
    }

    public int[] getPlayerCards(int player)
    {
        Integer[] playerTokens = tokens[player];

        int[] res = new int[3];
        for (int i = 0; i < numOfTokens[player]; i++) {
            res[i] = slotToCard[playerTokens[i]];
        }

        return res;
    }

    private void initializeData() {
        this.tokens = new Integer[env.config.players][3];
        this.numOfTokens = new Integer[env.config.players];

        for (Integer[] token: tokens)
        {
            Arrays.fill(token,-1);
        }

        Arrays.fill(numOfTokens,0);
    }
}
