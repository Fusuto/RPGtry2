package org.main.content;

import org.main.core.*;
import org.main.engine.MapEntity;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

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
                        InteractionSystem.option("I'll handle the slime.", () -> {
                            ensureSlimeForQuest(context);
                            context.getGameState().setQuestStage(QuestLibrary.SKELETON_HAT, 1);
                        }),
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
                        InteractionSystem.option("I'll ask the merchant.", () -> context.getGameState().setQuestStage(QuestLibrary.SKELETON_HAT, 3))
                );
            }

            if (questStage == 4) {
                InventorySystem.Inventory inventory = context.getGameState().getInventory();
                boolean hasHat = inventory.hasItemNamed(ItemLibrary.LEATHER_CAP.getDisplayName());
                LimbItem rewardLimb = ButcherySystem.recreateLimb(MonsterType.SKELETON, LimbSlot.HEAD, GearDurability.GOOD);
                String rewardText = "\n\nRewards:\n"
                        + QuestLibrary.skillExperienceRewardText(CharacterSkill.BUTCHERING, 45)
                        + "\n"
                        + QuestLibrary.skillExperienceRewardText(CharacterSkill.GRAFTING, 45)
                        + "\n"
                        + QuestLibrary.limbRewardText(rewardLimb);

                return InteractionSystem.dialogue(
                        npcName,
                        hasHat
                                ? "That cap! At last, I can be dead serious and properly dressed." + rewardText
                                : "I still feel a chilling draft where my dignity should be.",
                        null,
                        entity != null ? entity.getStaticImage() : null,
                        hasHat
                                ? InteractionSystem.option("Give the hat.", () -> {
                                    inventory.removeFirstItemNamed(ItemLibrary.LEATHER_CAP.getDisplayName());
                                    context.getGameState().getPlayerCharacter().addSkillExperience(CharacterSkill.BUTCHERING, 45);
                                    context.getGameState().getPlayerCharacter().addSkillExperience(CharacterSkill.GRAFTING, 45);
                                    context.getGameState().getInventory().addItem(rewardLimb);
                                    context.getGameState().setQuestStage(QuestLibrary.SKELETON_HAT, 5);
                                })
                                : InteractionSystem.closeOption("I'll find it."),
                        InteractionSystem.closeOption("Leave")
                );
            }

            if (questStage >= 5) {
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

            if (context.getGameState().getQuestStage(QuestLibrary.SKELETON_HAT) == 3) {
                return InteractionSystem.dialogue(
                        merchantName,
                        "A hat for the skeleton? Take this one. No charge. I prefer my customers with fewer haunting obligations.",
                        null,
                        entity != null ? entity.getStaticImage() : null,
                        InteractionSystem.option("Take the hat.", () -> {
                            context.getGameState().getInventory().addItem(ItemLibrary.LEATHER_CAP.createItem());
                            context.getGameState().setQuestStage(QuestLibrary.SKELETON_HAT, 4);
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

    CREST_FALLEN("crest_fallen") {
        @Override
        public InteractionSystem.Interaction create(InteractionSystem.InteractionContext context) {
            MapEntity entity = context.getEntity();
            String npcName = entity.getName();

            InteractionSystem.Conversation conversation =
                    new InteractionSystem.Conversation("start")
                            .addNode(new InteractionSystem.ConversationNode(
                                    "start",
                                    npcName,
                                    "Ah one of the undead, and you seem to even have your wits about you. Do you recall anything?",
                                    null,
                                    entity != null ? entity.getStaticImage() : null,
                                    "Player",
                                    npcName,
                                    new InteractionSystem.ConversationChoice("Who are you?", "who_are_you"),
                                    new InteractionSystem.ConversationChoice("What do you mean one of the undead?", "undead"),
                                    new InteractionSystem.ConversationChoice("I can't remember anything...", "amnesia"),
                                    new InteractionSystem.ConversationChoice("Goodbye.", null)
                            ))
                            .addNode(new InteractionSystem.ConversationNode(
                                    "who_are_you",
                                    npcName,
                                    "I am just a wanderer, not too different from yourself",
                                    null,
                                    entity != null ? entity.getStaticImage() : null,
                                    "Player",
                                    npcName,
                                    new InteractionSystem.ConversationChoice("What do you mean one of the undead?", "undead"),
                                    new InteractionSystem.ConversationChoice("I can't remember anything...", "amnesia"),
                                    new InteractionSystem.ConversationChoice("Goodbye.", null)
                            ))
                            .addNode(new InteractionSystem.ConversationNode(
                                    "undead",
                                    npcName,
                                    "Well it's just a guess, you might be something else all together. You definitely are not a normal person thats for sure.",
                                    null,
                                    entity.getStaticImage(),
                                    "Player",
                                    npcName,
                                    new InteractionSystem.ConversationChoice("Who are you?", "who_are_you"),
                                    new InteractionSystem.ConversationChoice("I can't remember anything...", "amnesia"),
                                    new InteractionSystem.ConversationChoice("Goodbye.", null)
                            ))
                            .addNode(new InteractionSystem.ConversationNode(
                                    "amnesia",
                                    npcName,
                                    "Well maybe what you were doing before might come back to your over time.",
                                    null,
                                    entity.getStaticImage(),
                                    "Player",
                                    npcName,
                                    new InteractionSystem.ConversationChoice("Who are you?", "who_are_you"),
                                    new InteractionSystem.ConversationChoice("What do you mean one of the undead?", "undead"),
                                    new InteractionSystem.ConversationChoice("Goodbye.", null)
                            ))

                    ;

            return InteractionSystem.conversation(conversation);

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

    private static void ensureSlimeForQuest(InteractionSystem.InteractionContext context) {
        boolean slimeExists = context.getGameState().getEntities().stream()
                .anyMatch(entity -> entity.getMonster() != null
                        && entity.getMonster().getType() == MonsterType.SLIME);

        if (!slimeExists) {
            context.getGameState().addEntity(new MapEntity(new Monster(MonsterType.SLIME), 8, 6));
        }
    }
}
