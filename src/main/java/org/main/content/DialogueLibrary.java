package org.main.content;

import org.main.core.InteractionSystem;
import org.main.core.ShopSystem;
import org.main.engine.MapEntity;

public enum DialogueLibrary {
    OLD_GUARD_INTRO("old_guard_intro") {
        @Override
        public InteractionSystem.Interaction create(InteractionSystem.InteractionContext context) {
            MapEntity entity = context.getEntity();
            String npcName = entity != null ? entity.getName() : "Old Guard";

            InteractionSystem.Conversation conversation =
                    new InteractionSystem.Conversation("start")
                            .addNode(new InteractionSystem.ConversationNode(
                                    "start",
                                    npcName,
                                    "You look new here. Are you heading deeper into the dungeon?",
                                    null,
                                    entity != null ? entity.getStaticImage() : null,
                                    "Player",
                                    npcName,
                                    new InteractionSystem.ConversationChoice("Who are you?", "who_are_you"),
                                    new InteractionSystem.ConversationChoice("What is below?", "below"),
                                    new InteractionSystem.ConversationChoice("Goodbye.", null)
                            ))
                            .addNode(new InteractionSystem.ConversationNode(
                                    "who_are_you",
                                    npcName,
                                    "I used to guard the lower floors. Now I mostly warn fools away from them.",
                                    null,
                                    entity != null ? entity.getStaticImage() : null,
                                    "Player",
                                    npcName,
                                    new InteractionSystem.ConversationChoice("What is below?", "below"),
                                    new InteractionSystem.ConversationChoice("Goodbye.", null)
                            ))
                            .addNode(new InteractionSystem.ConversationNode(
                                    "below",
                                    npcName,
                                    "Old things. Hungry things. If you go down there, keep your weapon close.",
                                    null,
                                    entity != null ? entity.getStaticImage() : null,
                                    "Player",
                                    npcName,
                                    new InteractionSystem.ConversationChoice("I understand.", null)
                            ));

            return InteractionSystem.conversation(conversation);
        }
    },

    MERCHANT_BASIC("merchant_basic") {
        @Override
        public InteractionSystem.Interaction create(InteractionSystem.InteractionContext context) {
            MapEntity entity = context.getEntity();
            String merchantName = entity != null ? entity.getName() : "Merchant";

            return InteractionSystem.dialogue(
                    merchantName,
                    "Looking to buy or sell?",
                    null,
                    entity != null ? entity.getStaticImage() : null,
                    InteractionSystem.option("Trade", () -> {
                        context.getGameState().openShop(
                                ShopSystem.createBasicMerchantShop(merchantName)
                        );
                    }),
                    InteractionSystem.closeOption("Leave")
            );
        }
    },

    CHEST_BASIC("chest_basic") {
        @Override
        public InteractionSystem.Interaction create(InteractionSystem.InteractionContext context) {
            MapEntity entity = context.getEntity();
            String chestName = entity != null ? entity.getName() : "Chest";

            return InteractionSystem.prompt(
                    chestName,
                    "Open the chest?",
                    InteractionSystem.option("Open", () -> {
                        System.out.println("Opened " + chestName + ".");
                        // Later: add loot here, then maybe remove or mark chest opened.
                    }),
                    InteractionSystem.closeOption("Leave")
            );
        }
    },

    DUNGEON_EXIT("dungeon_exit") {
        @Override
        public InteractionSystem.Interaction create(InteractionSystem.InteractionContext context) {
            return InteractionSystem.prompt(
                    "Dungeon Exit",
                    "Leave the dungeon?",
                    InteractionSystem.option("Yes", () -> {
                        System.out.println("Leaving dungeon...");
                        // Later: change area / load town / return to menu.
                    }),
                    InteractionSystem.closeOption("No")
            );
        }
    };

    private final String interactionId;

    DialogueLibrary(String interactionId) {
        this.interactionId = interactionId;
    }

    public String getInteractionId() {
        return interactionId;
    }

    public abstract InteractionSystem.Interaction create(InteractionSystem.InteractionContext context);
}
