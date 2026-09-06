# Modded registry compatibility

When synchronizing supported block tags, Grim skips block IDs that are absent
from PacketEvents' mappings. Known entries are retained; unknown entries are not
replaced with a different vanilla block or stored as null tag members. A tag
replacement still replaces the previous values, and unsupported client versions
keep their existing default tags.

This prevents the block-tag null dereference reported in #2855. It does **not**
add full modded-registry support. In particular, it does not resolve unknown item
IDs in inventory packets, supply collision data for custom blocks, or guarantee
correct checks for modded movement. The additional inventory error in #2855
requires a separate PacketEvents/registry compatibility investigation.

Tests cover unmapped and mapped IDs, mixed tag lists, replacement semantics and
client-version fallbacks. Live testing with More Chest Variants remains pending.
