/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.item.inventory.lens.impl.minecraft.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.lens.SlotProvider;
import org.spongepowered.common.item.inventory.lens.impl.comp.CraftingInventoryLensImpl;
import org.spongepowered.common.item.inventory.lens.impl.comp.GridInventoryLensImpl;
import org.spongepowered.common.item.inventory.lens.impl.comp.HotbarLensImpl;
import org.spongepowered.common.item.inventory.lens.impl.comp.OrderedInventoryLensImpl;
import org.spongepowered.common.item.inventory.lens.impl.slots.SlotLensImpl;

import java.util.Arrays;

public class ContainerPlayerInventoryLens extends ContainerLens {

    public ContainerPlayerInventoryLens(InventoryAdapter<IInventory, ItemStack> adapter, SlotProvider<IInventory, ItemStack> slots) {
        super(adapter, slots);
        this.init(slots);
    }

    @Override
    protected void init(SlotProvider<IInventory, ItemStack> slots) {
        final CraftingInventoryLensImpl crafting = new CraftingInventoryLensImpl(0, 1, 2, 2, 2, slots);
        final OrderedInventoryLensImpl armor = new OrderedInventoryLensImpl((1 + 4), 4, 1, slots);
        final GridInventoryLensImpl main = new GridInventoryLensImpl(((1 + 4) + 4), 9, 3, 9, slots);
        final HotbarLensImpl hotbar = new HotbarLensImpl((((1 + 4) + 4) + 27), 9, slots);
        final OrderedInventoryLensImpl offHand = new OrderedInventoryLensImpl(((((1 + 4) + 4) + 27) + 9), 1, 1, slots);

        // TODO actual Container order is:
        // CraftingOutput (1) -> Crafting (4) -> ArmorSlots (4) -> MainInventory (27) -> Hotbar (9) -> Offhand (1)
        // how to handle issues like in #939? ; e.g. Inventory#offer using a different insertion order
        this.viewedInventories = Arrays.asList(hotbar, main, armor, crafting, offHand);
        super.init(slots);
    }
}
