package org.main.content;

import org.main.core.InteractionSystem;
import org.main.core.InventorySystem;
import org.main.core.ShopSystem;
import org.main.engine.MapEntity;

public enum DialogueLibrary {
    OLD_GUARD_INTRO("old_guard_intro") {
        @Override
        public InteractionSystem.Interaction create(InteractionSystem.InteractionContext context) {
            MapEntity entity = context.getEntity();
            String npcName = entity != null ? entity.getName() : "Old Guard";
            int questStage = context.getGameState().getQuestStage(QuestLibrary.SKELETON_HAT);

            if (questStage == 0) {
                return InteractionSystem.dialogue(
                        npcName,
                        "Rattle me bones, traveler. Do me a kindness: thin out that slime nearby and I may remember where I left my manners.",
                        null,
                        entity != null ? entity.getStaticImage() : null,
                        InteractionSystem.option("I'll handle the slime.", () -> context.getGameState().setQuestStage(QuestLibrary.SKELETON_HAT, 1)),
                        InteractionSystem.closeOption("Not now")
                );
            }

            if (questStage == 1) {
                return InteractionSystem.dialogue(
                        npcName,
                        "The slime still jiggles. I can hear it mocking both of us.",
                        null,
                        entity != null ? entity.getStaticImage() : null,
                        InteractionSystem.closeOption("I'll be back.")
                );
            }

            if (questStage == 2) {
                return InteractionSystem.dialogue(
                        npcName,
                        "Fine work. Now for the important bit: I require a hat. The merchant owes me one, spiritually if not financially.",
                        null,
                        entity != null ? entity.getStaticImage() : null,
                        InteractionSystem.closeOption("I'll ask the merchant.")
                );
            }

            if (questStage == 3) {
                InventorySystem.Inventory inventory = context.getGameState().getInventory();
                boolean hasHat = inventory.hasItemNamed(ItemLibrary.LEATHER_CAP.getDisplayName());

                return InteractionSystem.dialogue(
                        npcName,
                        hasHat
                                ? "That cap! At last, I can be dead serious and properly dressed."
                                : "I still feel a chilling draft where my dignity should be.",
                        null,
                        entity != null ? entity.getStaticImage() : null,
                        hasHat
                                ? InteractionSystem.option("Give the hat.", () -> {
                                    inventory.removeFirstItemNamed(ItemLibrary.LEATHER_CAP.getDisplayName());
                                    context.getGameState().getPlayerCharacter().addClassExperience(90);
                                    context.getGameState().setQuestStage(QuestLibrary.SKELETON_HAT, 4);
                                })
                                : InteractionSystem.closeOption("I'll find it."),
                        InteractionSystem.closeOption("Leave")
                );
            }

            if (questStage >= 4) {
                return InteractionSystem.dialogue(
                        npcName,
                        "Thank you. I would tip my hat, but that would defeat the whole point.",
                        null,
                        entity != null ? entity.getStaticImage() : null,
                        InteractionSystem.closeOption("Goodbye.")
                );
            }

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

            if (context.getGameState().getQuestStage(QuestLibrary.SKELETON_HAT) == 2) {
                return InteractionSystem.dialogue(
                        merchantName,
                        "A hat for the skeleton? Take this one. No charge. I prefer my customers with fewer haunting obligations.",
                        null,
                        entity != null ? entity.getStaticImage() : null,
                        InteractionSystem.option("Take the hat.", () -> {
                            context.getGameState().getInventory().addItem(ItemLibrary.LEATHER_CAP.createItem());
                            context.getGameState().setQuestStage(QuestLibrary.SKELETON_HAT, 3);
                        }),
                        InteractionSystem.closeOption("Leave")
                );
            }

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
