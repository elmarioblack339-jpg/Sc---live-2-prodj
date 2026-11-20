#!/usr/bin/env python3
"""Regression checks for SC LIVE 2's pinned Browser WebView session.

These checks deliberately protect the current, user-confirmed D1/D2 hide-and-return
behavior. They do not claim YouTube audio DSP or process-death persistence.
"""
from pathlib import Path

source = Path("app/src/main/java/com/sclive2/dj/MainActivity.kt").read_text(encoding="utf-8")

def section(start, end):
    a = source.index(start)
    b = source.index(end, a)
    return source[a:b]

browser = section("    private fun showOnlineBrowser() {", "    private fun networkState()")
assert "if (browserDialog != null && browserWebView != null)" in browser
reuse = browser.index("if (browserDialog != null && browserWebView != null)")
reuse_end = browser.index("        val dialog = Dialog(this)", reuse)
reuse_block = browser[reuse:reuse_end]
assert "browserDialog?.show()" in reuse_block
assert "return" in reuse_block
assert "browser.loadUrl(" not in reuse_block, "Reopening an existing Browser session must not reload its page"

assert 'addBrowserButton(actions, "DECK 1"' in browser
assert 'addBrowserButton(actions, "DECK 2"' in browser
for deck in ('"DECK 1"', '"DECK 2"'):
    button = browser.index(f'addBrowserButton(actions, {deck}')
    next_button = browser.find('addBrowserButton(actions,', button + 1)
    block = browser[button: next_button if next_button != -1 else len(browser)]
    assert "browserPinnedDeck =" in block and "dialog.hide()" in block, f"{deck} must pin and hide, not dismiss"

close_start = browser.index('addBrowserButton(actions, "CERRAR"')
close_end = browser.index("        root.addView(actions)", close_start)
close_block = browser[close_start:close_end]
assert "browserPinnedDeck = null" in close_block and "dialog.dismiss()" in close_block

dismiss_start = browser.index("dialog.setOnDismissListener")
dismiss_end = browser.index("        dialog.show()", dismiss_start)
dismiss_block = browser[dismiss_start:dismiss_end]
assert "browser.destroy()" in dismiss_block
assert "browserWebView = null" in dismiss_block

print("Browser session regression checks passed: D1/D2 hide, reopen reuses same WebView, CERRAR destroys it.")
print("Not covered: YouTube audio DSP, Android process death, or device-specific background playback.")
