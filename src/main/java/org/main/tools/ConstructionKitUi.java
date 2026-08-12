package org.main.tools;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

/** Shared layout and window policy for Construction Kit authoring UI. */
final class ConstructionKitUi {
    static final int GAP = 6;
    static final int SECTION_GAP = 10;
    static final int LABEL_WIDTH = 170;
    static final int ROW_HEIGHT = 28;

    enum DialogProfile {
        COMPACT(new Dimension(560, 380), new Dimension(500, 300)),
        STANDARD(new Dimension(860, 620), new Dimension(760, 500)),
        LARGE(new Dimension(1280, 820), new Dimension(1050, 700));

        private final Dimension initialSize;
        private final Dimension minimumSize;

        DialogProfile(Dimension initialSize, Dimension minimumSize) {
            this.initialSize = initialSize;
            this.minimumSize = minimumSize;
        }
    }

    private ConstructionKitUi() {
    }

    static JPanel formPanel() {
        return new FormPanel();
    }

    static JPanel formRow(String label, Component component) {
        return new FormRow(label, component);
    }

    static JPanel inlineFields(Component primary, Component... trailing) {
        JPanel panel = new JPanel(new BorderLayout(GAP, 0));
        panel.add(primary == null ? new JPanel() : primary, BorderLayout.CENTER);
        if (trailing != null && trailing.length > 0) {
            JPanel buttons = new JPanel(new java.awt.GridLayout(1, 0, GAP, 0));
            for (Component component : trailing) {
                if (component != null) {
                    buttons.add(component);
                }
            }
            panel.add(buttons, BorderLayout.EAST);
        }
        return panel;
    }

    static JPanel buttonRow(Component... actions) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, GAP, 0));
        if (actions != null) {
            for (Component action : actions) {
                if (action != null) {
                    panel.add(action);
                }
            }
        }
        return panel;
    }

    static JPanel section(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(GAP, GAP));
        panel.setBorder(BorderFactory.createTitledBorder(title == null ? "" : title));
        panel.add(content == null ? new JPanel() : content, BorderLayout.CENTER);
        return panel;
    }

    static JPanel topAligned(Component component) {
        if (component == null) {
            return new JPanel();
        }
        JPanel wrapper = new TopAlignedPanel();
        wrapper.setBorder(BorderFactory.createEmptyBorder(SECTION_GAP, SECTION_GAP, SECTION_GAP, SECTION_GAP));
        wrapper.add(component, BorderLayout.NORTH);
        return wrapper;
    }

    static JScrollPane scrollingForm(Component component) {
        JScrollPane scrollPane = new JScrollPane(
                component,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        return scrollPane;
    }

    static int showFormDialog(
            Component parent,
            Component form,
            String title,
            Map<String, Dimension> rememberedSizes
    ) {
        Window owner = parent instanceof Window window
                ? window
                : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(true);

        Component content = prepareDialogContent(form);
        configureComponentTree(content);
        DialogProfile profile = profileFor(form);

        JPanel root = new JPanel(new BorderLayout(SECTION_GAP, SECTION_GAP));
        root.setBorder(BorderFactory.createEmptyBorder(SECTION_GAP, SECTION_GAP, SECTION_GAP, SECTION_GAP));
        root.add(content, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, GAP, 0));
        buttons.add(okButton);
        buttons.add(cancelButton);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(okButton);

        int[] result = {javax.swing.JOptionPane.CLOSED_OPTION};
        Runnable accept = () -> {
            result[0] = javax.swing.JOptionPane.OK_OPTION;
            dialog.dispose();
        };
        Runnable cancel = () -> {
            result[0] = javax.swing.JOptionPane.CANCEL_OPTION;
            dialog.dispose();
        };
        okButton.addActionListener(event -> accept.run());
        cancelButton.addActionListener(event -> cancel.run());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                cancel.run();
            }
        });
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "construction-kit-cancel");
        dialog.getRootPane().getActionMap().put("construction-kit-cancel", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                cancel.run();
            }
        });

        Rectangle workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension minimum = bounded(profile.minimumSize, workArea);
        String sizeKey = stableDialogId(title);
        Dimension remembered = rememberedSizes == null ? null : rememberedSizes.get(sizeKey);
        Dimension requested = remembered == null ? profile.initialSize : remembered;
        Dimension size = boundedAtLeast(requested, minimum, workArea);
        dialog.setMinimumSize(minimum);
        dialog.setSize(size);
        dialog.setLocationRelativeTo(owner == null ? parent : owner);
        dialog.setVisible(true);
        if (rememberedSizes != null) {
            rememberedSizes.put(sizeKey, dialog.getSize());
        }
        return result[0];
    }

    static void showManagedDialog(JDialog dialog, Map<String, Dimension> rememberedSizes) {
        if (dialog == null) {
            return;
        }
        dialog.setResizable(true);
        configureComponentTree(dialog.getContentPane());
        dialog.pack();

        DialogProfile profile;
        if (contains(dialog.getContentPane(), JSplitPane.class)) {
            profile = DialogProfile.LARGE;
        } else if (contains(dialog.getContentPane(), JTabbedPane.class)
                || dialog.getWidth() > DialogProfile.COMPACT.initialSize.width
                || dialog.getHeight() > DialogProfile.COMPACT.initialSize.height) {
            profile = DialogProfile.STANDARD;
        } else {
            profile = DialogProfile.COMPACT;
        }

        Rectangle workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension minimum = bounded(profile.minimumSize, workArea);
        String sizeKey = stableDialogId(dialog.getTitle());
        Dimension remembered = rememberedSizes == null ? null : rememberedSizes.get(sizeKey);
        Dimension natural = new Dimension(
                Math.max(dialog.getWidth(), profile.initialSize.width),
                Math.max(dialog.getHeight(), profile.initialSize.height));
        Dimension requested = remembered == null ? natural : remembered;
        dialog.setMinimumSize(minimum);
        dialog.setSize(boundedAtLeast(requested, minimum, workArea));
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
        if (rememberedSizes != null) {
            rememberedSizes.put(sizeKey, dialog.getSize());
        }
    }

    static Component messageContent(Component content) {
        if (content instanceof JTextArea area) {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setCaretPosition(0);
            return scrollingForm(area);
        }
        configureComponentTree(content);
        return content;
    }

    static void configureWorkspace(JComponent workspace) {
        configureComponentTree(workspace);
        workspace.setBorder(BorderFactory.createEmptyBorder(SECTION_GAP, SECTION_GAP, SECTION_GAP, SECTION_GAP));
    }

    static void configureWorkspace(JDialog workspace) {
        if (workspace == null) {
            return;
        }
        configureComponentTree(workspace.getContentPane());
        if (workspace.getContentPane() instanceof JComponent content) {
            configureWorkspace(content);
        }
        Rectangle workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        workspace.setMinimumSize(bounded(DialogProfile.LARGE.minimumSize, workArea));
        workspace.setPreferredSize(bounded(DialogProfile.LARGE.initialSize, workArea));
    }

    static void configureComponentTree(Component component) {
        if (component == null) {
            return;
        }
        if (component instanceof JTabbedPane tabs) {
            tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        }
        if (component instanceof JScrollPane scrollPane) {
            scrollPane.getVerticalScrollBar().setUnitIncrement(18);
            Component view = scrollPane.getViewport().getView();
            if (view instanceof FormPanel || view instanceof FormRow) {
                scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            }
        }
        if (component instanceof JSplitPane splitPane) {
            splitPane.setContinuousLayout(true);
            splitPane.setOneTouchExpandable(true);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                configureComponentTree(child);
            }
        }
    }

    private static Component prepareDialogContent(Component form) {
        if (form == null) {
            return new JPanel();
        }
        if (form instanceof FormPanel || form instanceof FormRow) {
            return scrollingForm(topAligned(form));
        }
        if (form instanceof JScrollPane || form instanceof JTabbedPane || form instanceof JSplitPane) {
            return form;
        }
        return form;
    }

    private static DialogProfile profileFor(Component form) {
        if (contains(form, JTabbedPane.class)) {
            return DialogProfile.STANDARD;
        }
        if (contains(form, JSplitPane.class)) {
            return DialogProfile.LARGE;
        }
        if (form instanceof FormPanel panel && panel.getComponentCount() <= 7) {
            return DialogProfile.COMPACT;
        }
        return DialogProfile.STANDARD;
    }

    private static boolean contains(Component component, Class<? extends Component> type) {
        if (component == null) {
            return false;
        }
        if (type.isInstance(component)) {
            return true;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (contains(child, type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String stableDialogId(String title) {
        String safe = title == null || title.isBlank() ? "construction-kit-popup" : title.trim();
        return safe.replaceFirst("^(Create|Edit)\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static Dimension bounded(Dimension requested, Rectangle workArea) {
        return new Dimension(
                Math.max(360, Math.min(requested.width, workArea.width)),
                Math.max(240, Math.min(requested.height, workArea.height)));
    }

    private static Dimension boundedAtLeast(Dimension requested, Dimension minimum, Rectangle workArea) {
        return new Dimension(
                Math.min(workArea.width, Math.max(minimum.width, requested.width)),
                Math.min(workArea.height, Math.max(minimum.height, requested.height)));
    }

    private static final class FormPanel extends JPanel implements Scrollable {
        private FormPanel() {
            super();
            setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ROW_HEIGHT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(ROW_HEIGHT, visibleRect.height - ROW_HEIGHT);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class TopAlignedPanel extends JPanel implements Scrollable {
        private TopAlignedPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ROW_HEIGHT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(ROW_HEIGHT, visibleRect.height - ROW_HEIGHT);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class FormRow extends JPanel implements Scrollable {
        private FormRow(String label, Component component) {
            super(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
            JLabel rowLabel = new JLabel(label == null ? "" : label, SwingConstants.LEADING);
            Dimension labelSize = new Dimension(LABEL_WIDTH, ROW_HEIGHT);
            rowLabel.setPreferredSize(labelSize);
            rowLabel.setMinimumSize(labelSize);

            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = 0;
            labelConstraints.weightx = 0;
            labelConstraints.fill = GridBagConstraints.HORIZONTAL;
            labelConstraints.anchor = GridBagConstraints.NORTHWEST;
            labelConstraints.insets = new Insets(0, 0, 0, GAP);
            add(rowLabel, labelConstraints);

            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.gridx = 1;
            fieldConstraints.gridy = 0;
            fieldConstraints.weightx = 1;
            fieldConstraints.weighty = component instanceof JScrollPane ? 1 : 0;
            fieldConstraints.fill = component instanceof JScrollPane
                    ? GridBagConstraints.BOTH
                    : GridBagConstraints.HORIZONTAL;
            fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
            add(component == null ? new JPanel() : component, fieldConstraints);

            setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension preferred = getPreferredSize();
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(ROW_HEIGHT, preferred.height)));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ROW_HEIGHT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ROW_HEIGHT;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
