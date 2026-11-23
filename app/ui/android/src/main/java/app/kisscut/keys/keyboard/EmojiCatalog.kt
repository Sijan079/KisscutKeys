package app.kisscut.keys.keyboard

import androidx.annotation.StringRes
import app.kisscut.keys.R

/**
 * The emoji on the Emoji tab (docs/KEYBOARD.md, Emoji tab), by category. Kept in code, not `strings.xml`
 * (UI strings carry no emoji). Each entry is one emoji, separated by spaces; the keyboard drops the ones the
 * phone's font cannot draw.
 */
enum class EmojiCategory(@StringRes val label: Int, private val list: String) {
    SMILEYS(
        R.string.kb_emoji_smileys,
        "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 " +
            "🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 " +
            "🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 " +
            "💀 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾",
    ),
    PEOPLE(
        R.string.kb_emoji_people,
        "👋 🤚 🖐 ✋ 🖖 👌 🤏 ✌ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 💪 " +
            "👀 👁 👅 👄 🧠 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🙇 🤦 🤷 👮 🕵 💂 👷 " +
            "🤴 👸 👳 👲 🧕 🤵 👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💃 🕺 👯 🧖 🧗 🏃 🚶 🧍 🧎",
    ),
    ANIMALS(
        R.string.kb_emoji_animals,
        "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 " +
            "🐝 🐛 🦋 🐌 🐞 🐜 🦗 🕷 🦂 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦒 " +
            "🐪 🐫 🦘 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🐇 🦝 🦔 🐾 🐉 🌵 🎄 🌲 🌳 🌴 " +
            "🌱 🌿 ☘ 🍀 🍁 🍂 🍃 🍄 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌚 🌙 ⭐ 🌟 ✨ ⚡ 🔥 🌈 ☀ ⛅ ☁ 🌧 ⛄ ❄ 💧 🌊",
    ),
    FOOD(
        R.string.kb_emoji_food,
        "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶 🌽 🥕 🧄 🧅 🥔 🍠 🥐 " +
            "🥯 🍞 🥖 🧀 🥚 🍳 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 " +
            "🍘 🍥 🥠 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 🍼 ☕ 🍵 🧃 🥤 🍶 🍺 🍻 🥂 🍷",
    ),
    ACTIVITIES(
        R.string.kb_emoji_activities,
        "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 ⛸ 🥌 🎿 ⛷ 🏂 🏋 " +
            "🤸 🤺 🤾 🏌 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖 🎗 🎫 🎟 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 " +
            "🥁 🎷 🎺 🎸 🎻 🎲 ♟ 🎯 🎳 🎮 🎰 🧩 🎉 🎊 🎈 🎁 🎀 🪅",
    ),
    TRAVEL(
        R.string.kb_emoji_travel,
        "🚗 🚕 🚙 🚌 🚎 🏎 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛴 🚲 🛵 🏍 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 " +
            "🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🛳 ⛴ 🚢 ⚓ ⛽ 🚧 🚦 🗺 🗿 🗽 🗼 🏰 🏯 🏟 🎡 " +
            "🎢 🎠 ⛲ 🏖 🏝 🏜 🌋 ⛰ 🏔 🗻 🏕 ⛺ 🏠 🏡 🏘 🏗 🏭 🏢 🏬 🏣 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛 ⛪ 🕌",
    ),
    OBJECTS(
        R.string.kb_emoji_objects,
        "⌚ 📱 💻 ⌨ 🖥 🖨 🖱 💽 💾 💿 📀 📷 📸 📹 🎥 📞 ☎ 📺 📻 🎙 ⏰ ⌛ ⏳ 📡 🔋 🔌 💡 🔦 🕯 🧯 " +
            "💸 💵 💰 💳 💎 🔧 🔨 🛠 🔩 ⚙ 🧱 🔫 💣 🔪 🛡 🚬 🔮 🧿 💈 🔭 🔬 💊 💉 🧬 🦠 🧹 🧺 🧻 🚽 🛁 " +
            "🔑 🗝 🚪 🛋 🛏 🧸 🖼 🛍 🛒 🎁 ✉ 📩 📦 📫 📜 📄 📅 📆 📇 📈 📉 📊 📋 📌 📍 📎 📏 📐 ✂ 🔒 " +
            "🔓 🖊 🖋 ✒ 📝 ✏ 🔍 🔎 📚 📖 🔖 🏷",
    ),
    SYMBOLS(
        R.string.kb_emoji_symbols,
        "❤ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣ 💕 💞 💓 💗 💖 💘 💝 💟 ☮ ✝ ☪ 🕉 ☸ ✡ ☯ ☦ 🛐 ⛎ ♈ " +
            "♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛ ☢ ☣ 📴 📳 🈶 🆚 💮 🉐 ㊙ ㊗ 🅰 🅱 🆎 🆑 🅾 🆘 ❌ " +
            "⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ ‼ ⁉ 🔅 🔆 ⚠ 🚸 🔱 ⚜ 🔰 ♻ ✅ " +
            "💹 ❇ ✳ ❎ 🌐 💠 Ⓜ 🌀 💤 🏧 🚾 ♿ 🅿 🚹 🚺 🚼 🚻 🎦 📶 🆗 🆙 🆒 🆕 🆓 🔟 🔢 #⃣ ▶ ⏸ ⏹ " +
            "⏺ ⏭ ⏮ ⏩ ⏪ 🔀 🔁 🔂 ◀ 🔼 🔽 ⏫ ⏬ ➡ ⬅ ⬆ ⬇ ↗ ↘ ↙ ↖ ↕ ↔ 🔄 ➕ ➖ ➗ ✖ ♾ 💲 " +
            "™ © ® 〰 ➰ ➿ 🔚 🔙 🔛 🔝 🔜 ✔ ☑ 🔘 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜",
    ),
    ;

    /** This category's emoji, in order. */
    val emoji: List<String> get() = list.split(' ')
}
