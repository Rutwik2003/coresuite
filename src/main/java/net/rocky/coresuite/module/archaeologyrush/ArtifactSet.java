package net.rocky.coresuite.module.archaeologyrush;

import org.bukkit.Material;
import java.util.List;

public record ArtifactSet(
        String id,
        String displayName,
        String description,
        Material displayItem,
        List<ArtifactFragment> fragments,
        double rewardMoney,
        List<String> rewardCommands
) {
    public int getPieceCount() {
        return fragments.size();
    }

    public ArtifactFragment getFragment(int index) {
        if (index < 1 || index > fragments.size()) {
            return null;
        }
        return fragments.get(index - 1); // Assuming fragments are ordered and 1-indexed mapping to 0-indexed list
    }
}
