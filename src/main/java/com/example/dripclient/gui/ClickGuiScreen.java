package com.example.dripclient.gui;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.ModuleManager;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** ClickGUI w stylu "blokowym 3D": kwadratowe panele ze skosami, cieniem i wytloczeniem. */
public class ClickGuiScreen extends Screen {

    private static final int PW = 132, HEAD = 20, ROW = 18;
    private static final int ACC1 = 0xFF7C5CFF, ACC2 = 0xFF3FA9F5;
    private static final int PANEL = 0xFF1E212B;
    private static final int BTN = 0xFF2C3040, BTN_HOVER = 0xFF3A4058, BTN_ON = 0xFF3C3490;
    private static final int LIGHT = 0xFF5F6690, DARK = 0xFF07080B;
    private static final int TXT = 0xFFFFFFFF, DIM = 0xFFA0A4B0;

    private static final long OPEN_MS = 260;
    private static final float BREAK_SEC = 0.7f;

    private static final Map<String, int[]> POS = new HashMap<>();

    private static final class PanelState {
        final String title;
        final Category cat;
        int x, y;

        PanelState(String title, Category cat, int x, int y) {
            this.title = title;
            this.cat = cat;
            this.x = x;
            this.y = y;
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right,
                       Setting.Num slider, PanelState drag) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Shard(float x, float y, float vx, float vy, float rot, float vrot, int color) {
    }

    private final List<PanelState> panels = new ArrayList<>();
    private final Set<Module> expanded = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<int[]> rects = new ArrayList<>();
    private final Random rng = new Random();
    private final List<Shard> shards = new ArrayList<>();

    private PanelState dragPanel;
    private int dragOffX, dragOffY;
    private Setting.Num dragSlider;
    private int sliderX, sliderW;

    private boolean inputFocused;
    private final StringBuilder input = new StringBuilder();

    private final long openStart;
    private boolean closing;
    private long closeStart;
    private float alpha = 0f;

    public ClickGuiScreen() {
        super(Text.literal("Mojang"));
        int i = 0;
        for (Category c : Category.values()) {
            boolean has = false;
            for (Module m : ModuleManager.MODULES) if (m.category == c) has = true;
            if (has) addPanel(c.label, c, i++);
        }
        openStart = Util.getMeasuringTimeMs();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f));
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 0.7f));
    }

    private void addPanel(String title, Category cat, int index) {
        int[] saved = POS.get(title);
        int x = saved != null ? saved[0] : 30 + index * (PW + 14);
        int y = saved != null ? saved[1] : 34;
        panels.add(new PanelState(title, cat, x, y));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ pomocnicze

    private int a(int c) {
        int al = (int) (((c >>> 24) & 0xFF) * alpha);
        return (al << 24) | (c & 0xFFFFFF);
    }

    private void text(DrawContext ctx, String s, int x, int y, int color) {
        if (((color >>> 24) & 0xFF) * alpha < 8) return;
        ctx.drawTextWithShadow(textRenderer, s, x, y, a(color));
    }

    private void play(float pitch) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    /** Kwadrat ze skosem: raised = wypukly, inaczej wklesly. */
    private void bevel(DrawContext ctx, int x, int y, int w, int h, int base, boolean raised) {
        ctx.fill(x, y, x + w, y + h, a(base));
        int light = a(raised ? LIGHT : DARK);
        int dark = a(raised ? DARK : LIGHT);
        ctx.fill(x, y, x + w, y + 1, light);
        ctx.fill(x, y, x + 1, y + h, light);
        ctx.fill(x, y + h - 1, x + w, y + h, dark);
        ctx.fill(x + w - 1, y, x + w, y + h, dark);
    }

    // ------------------------------------------------------------------ rysowanie

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = Util.getMeasuringTimeMs();

        if (closing) {
            renderShards(ctx, now);
            return;
        }

        float p = MathHelper.clamp((now - openStart) / (float) OPEN_MS, 0f, 1f);
        alpha = 1f - (1f - p) * (1f - p) * (1f - p);

        ctx.fill(0, 0, width, height, a(0x80000000));

        hits.clear();
        rects.clear();

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(0f, (1f - alpha) * -18f, 0f);
        for (PanelState ps : panels) drawPanel(ctx, ps, mx, my);
        m.pop();
    }

    private int settingsHeight(Module m) {
        int h = 0;
        for (Setting s : m.settings) h += (s instanceof Setting.Num) ? 24 : 16;
        if (m.hasFriends) h += friendsHeight();
        return h;
    }

    private int friendsHeight() {
        return 20 + 14 * ModuleManager.FRIENDS.size() + 14;
    }

    private int moduleHeight(Module m) {
        return ROW + (expanded.contains(m) ? settingsHeight(m) : 0);
    }

    private int bodyHeight(PanelState ps) {
        int h = 4;
        for (Module m : ModuleManager.MODULES) if (m.category == ps.cat) h += moduleHeight(m);
        return h;
    }

    private void drawPanel(DrawContext ctx, PanelState ps, int mx, int my) {
        int x = ps.x, y = ps.y;
        int totalH = HEAD + bodyHeight(ps);

        // wytloczenie (efekt 3D): kilka przesunietych, coraz ciemniejszych warstw
        for (int i = 5; i >= 1; i--) {
            int shade = 0x20 + (5 - i) * 6;
            ctx.fill(x + i, y + i, x + PW + i, y + totalH + i, a(0xFF000000 | (shade << 16) | (shade << 8) | (shade + 8)));
        }
        ctx.fill(x + 6, y + 6, x + PW + 8, y + totalH + 8, a(0x55000000));

        // korpus
        bevel(ctx, x, y, PW, totalH, PANEL, true);

        // naglowek
        ctx.fillGradient(x + 1, y + 1, x + PW - 1, y + HEAD, a(ACC1), a(ACC2));
        ctx.fill(x + 1, y + 1, x + PW - 1, y + 2, a(0x66FFFFFF));
        ctx.fill(x + 1, y + HEAD - 1, x + PW - 1, y + HEAD, a(0x66000000));
        text(ctx, ps.title, x + PW / 2 - textRenderer.getWidth(ps.title) / 2, y + 6, TXT);
        hits.add(new Hit(x, y, PW, HEAD, null, null, null, ps));

        int cy = y + HEAD + 2;
        for (Module m : ModuleManager.MODULES) {
            if (m.category == ps.cat) cy = drawModule(ctx, m, x, cy, mx, my);
        }
        rects.add(new int[]{x, y, PW, totalH});
    }

    private int drawModule(DrawContext ctx, Module m, int px, int cy, int mx, int my) {
        int x = px + 3, w = PW - 6;
        boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + ROW;

        int base = m.enabled ? BTN_ON : (hover ? BTN_HOVER : BTN);
        bevel(ctx, x, cy, w, ROW - 2, base, !m.enabled);
        if (m.enabled) {
            ctx.fillGradient(x + 1, cy + 1, x + 4, cy + ROW - 3, a(ACC1), a(ACC2));
        }
        text(ctx, m.name, x + 9, cy + 5, m.enabled ? TXT : 0xFFD0D3DE);
        if (!m.settings.isEmpty() || m.hasFriends) {
            text(ctx, expanded.contains(m) ? "-" : "+", x + w - 10, cy + 5, DIM);
        }
        hits.add(new Hit(x, cy, w, ROW - 2,
                () -> { m.toggle(); play(m.enabled ? 1.4f : 0.9f); },
                () -> { if (!expanded.remove(m)) expanded.add(m); play(1.1f); },
                null, null));
        cy += ROW;

        if (expanded.contains(m)) {
            for (Setting s : m.settings) {
                if (s instanceof Setting.Bool b) {
                    ctx.fill(x, cy, x + w, cy + 16, a(0xFF14161D));
                    text(ctx, b.name, x + 6, cy + 4, 0xFFE0E2EA);
                    bevel(ctx, x + w - 16, cy + 3, 10, 10, b.value ? ACC1 : 0xFF23262F, !b.value);
                    final Setting.Bool bb = b;
                    hits.add(new Hit(x, cy, w, 16, () -> { bb.value = !bb.value; play(bb.value ? 1.4f : 0.9f); },
                            null, null, null));
                    cy += 16;
                } else if (s instanceof Setting.Num n) {
                    ctx.fill(x, cy, x + w, cy + 24, a(0xFF14161D));
                    text(ctx, n.name, x + 6, cy + 3, 0xFFE0E2EA);
                    String v = (n.step >= 1) ? String.valueOf((int) n.value) : String.format(Locale.ROOT, "%.2f", n.value);
                    text(ctx, v, x + w - 6 - textRenderer.getWidth(v), cy + 3, ACC2);
                    int tx = x + 6, tw = w - 12, ty = cy + 15;
                    bevel(ctx, tx, ty, tw, 6, 0xFF0C0D12, false);
                    float t = (float) ((n.value - n.min) / (n.max - n.min));
                    int fw = (int) ((tw - 2) * MathHelper.clamp(t, 0f, 1f));
                    ctx.fillGradient(tx + 1, ty + 1, tx + 1 + fw, ty + 5, a(ACC1), a(ACC2));
                    bevel(ctx, tx + fw - 1, ty - 3, 6, 12, 0xFFDDE1F2, true);
                    hits.add(new Hit(x, cy, w, 24, null, null, n, null));
                    cy += 24;
                }
            }
            if (m.hasFriends) cy = drawFriends(ctx, x, w, cy, mx, my);
        }
        return cy;
    }

    private int drawFriends(DrawContext ctx, int x, int w, int cy, int mx, int my) {
        int top = cy;
        ctx.fill(x, cy, x + w, cy + friendsHeight(), a(0xFF14161D));
        text(ctx, "Znajomi", x + 6, cy + 4, ACC2);
        cy += 14;

        bevel(ctx, x + 4, cy, w - 8, 14, inputFocused ? 0xFF111319 : 0xFF0C0D12, false);
        boolean blink = (Util.getMeasuringTimeMs() / 500) % 2 == 0;
        if (input.length() == 0 && !inputFocused) {
            text(ctx, "nick + Enter", x + 8, cy + 3, DIM);
        } else {
            text(ctx, input + (inputFocused && blink ? "_" : ""), x + 8, cy + 3, TXT);
        }
        hits.add(new Hit(x, cy, w, 14, () -> inputFocused = true, null, null, null));
        cy += 14 + 6;

        for (String n : new ArrayList<>(ModuleManager.FRIENDS)) {
            boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + 14;
            if (hover) ctx.fill(x, cy, x + w, cy + 14, a(0x30FFFFFF));
            text(ctx, n, x + 6, cy + 3, 0xFF55FF55);
            text(ctx, "x", x + w - 10, cy + 3, 0xFFFF6666);
            hits.add(new Hit(x, cy, w, 14, () -> { ModuleManager.removeFriend(n); play(0.9f); }, null, null, null));
            cy += 14;
        }
        text(ctx, "MMB na graczu = znajomy", x + 6, cy + 3, DIM);
        return top + friendsHeight();
    }

    // ------------------------------------------------------------------ rozpad GUI

    private void startClose() {
        if (closing) return;
        closing = true;
        closeStart = Util.getMeasuringTimeMs();
        alpha = 1f;
        inputFocused = false;
        shards.clear();

        int tile = 10;
        for (int[] r : rects) {
            float rcx = r[0] + r[2] / 2f;
            for (int ty = 0; ty < r[3]; ty += tile) {
                for (int tx = 0; tx < r[2]; tx += tile) {
                    float cx = r[0] + tx + tile / 2f;
                    float cy = r[1] + ty + tile / 2f;
                    int color;
                    if (ty < HEAD) {
                        color = (tx < r[2] / 2) ? ACC1 : ACC2;
                    } else {
                        int j = rng.nextInt(28);
                        color = 0xFF000000 | ((0x1C + j) << 16) | ((0x1F + j) << 8) | (0x2B + j);
                    }
                    float vx = (cx - rcx) * 1.6f + (rng.nextFloat() - 0.5f) * 90f;
                    float vy = -40f - rng.nextFloat() * 220f;
                    shards.add(new Shard(cx, cy, vx, vy, 0f, (rng.nextFloat() - 0.5f) * 14f, color));
                }
            }
        }
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_GLASS_BREAK, 1.0f));
    }

    private void renderShards(DrawContext ctx, long now) {
        float t = (now - closeStart) / 1000f;
        if (t >= BREAK_SEC) {
            ModuleManager.save();
            client.setScreen(null);
            return;
        }
        alpha = 1f - t / BREAK_SEC;
        ctx.fill(0, 0, width, height, a(0x80000000));

        MatrixStack m = ctx.getMatrices();
        for (Shard s : shards) {
            float px = s.x() + s.vx() * t;
            float py = s.y() + s.vy() * t + 0.5f * 700f * t * t;
            float rot = s.rot() + s.vrot() * t;
            int half = Math.max(2, Math.round(5f * (1f - 0.35f * t)));
            m.push();
            m.translate(px, py, 0f);
            m.multiply(RotationAxis.POSITIVE_Z.rotation(rot));
            ctx.fill(-half, -half, half, half, a(DARK));
            ctx.fill(-half + 1, -half + 1, half - 1, half - 1, a(s.color()));
            ctx.fill(-half + 1, -half + 1, half - 1, -half + 2, a(0x55FFFFFF));
            m.pop();
        }
    }

    // ------------------------------------------------------------------ wejscie

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing || alpha < 0.9f) return true;
        inputFocused = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.in(mx, my)) continue;
            if (button == 0 && h.slider() != null) {
                dragSlider = h.slider();
                sliderX = h.x() + 6;
                sliderW = h.w() - 12;
                setSliderFrom(mx);
                return true;
            }
            if (button == 0 && h.drag() != null) {
                dragPanel = h.drag();
                dragOffX = (int) mx - dragPanel.x;
                dragOffY = (int) my - dragPanel.y;
                return true;
            }
            if (button == 0 && h.left() != null) {
                h.left().run();
                return true;
            }
            if (button == 1 && h.right() != null) {
                h.right().run();
                return true;
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (closing) return true;
        if (dragSlider != null) {
            setSliderFrom(mx);
            return true;
        }
        if (dragPanel != null) {
            dragPanel.x = MathHelper.clamp((int) mx - dragOffX, 0, Math.max(0, width - PW));
            dragPanel.y = MathHelper.clamp((int) my - dragOffY, 0, Math.max(0, height - HEAD));
            POS.put(dragPanel.title, new int[]{dragPanel.x, dragPanel.y});
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragSlider = null;
        dragPanel = null;
        return true;
    }

    private void setSliderFrom(double mx) {
        float t = MathHelper.clamp((float) ((mx - sliderX) / sliderW), 0f, 1f);
        dragSlider.set(dragSlider.min + t * (dragSlider.max - dragSlider.min));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            startClose();
            return true;
        }
        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                ModuleManager.addFriend(input.toString());
                input.setLength(0);
                play(1.4f);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && input.length() > 0) {
                input.setLength(input.length() - 1);
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_P) {
            startClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused && !closing && (Character.isLetterOrDigit(chr) || chr == '_') && input.length() < 16) {
            input.append(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        startClose();
    }
}
