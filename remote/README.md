# JARVIS BLUE — iPhone → Windows anywhere (private beta)

This is a limited-command prototype, not a finished or security-audited release.

## Windows PC
1. Install Python 3.10+ and Tailscale on the Windows PC. Sign in to Tailscale.
2. Install Tailscale on iPhone and sign in to the **same tailnet**. Enable its VPN connection.
3. In **PowerShell** in the repository root, create a random secret for this terminal session:
   `$env:JARVIS_REMOTE_TOKEN = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`
   Copy the token securely to iPhone. **Never commit it, paste it into chat, or share it.**
4. Run `python remote/agent.py`. It binds to `127.0.0.1:8765` only.
5. In another PowerShell window, run `tailscale serve --bg 8765` and `tailscale serve status`. Use the private **HTTPS** URL Tailscale reports. Do **not** use `tailscale funnel`.
6. On iPhone, while Tailscale is connected, open `https://YOUR-TAILSCALE-HOST/remote.html` in Safari. Use Share → Add to Home Screen. Enter your secret token, then tap a command.

Tailscale Serve syntax can change; consult `tailscale serve --help` for your installed version. Only devices authorized in your tailnet can reach the Serve address, subject to your tailnet ACLs. Use device approval/MFA on your Tailscale account. The PC must be on, connected to Tailscale, and logged into the desktop for GUI actions.

## Security boundaries
- Agent binds only to loopback, uses a 256-bit random bearer token, restricts commands to a fixed allowlist, and does not run arbitrary user-supplied commands.
- The token stays in the PWA page memory, not localStorage. Reloading the page requires entering it again.
- The page and API are served from the same HTTPS origin by Tailscale Serve; do not expose the HTTP port to the Internet.
- Do not use a public static web host to run `remote.html` against an HTTP agent; HTTPS mixed-content and cross-origin rules prevent it, intentionally.
- The browser UI currently uses buttons, not natural-language command parsing. This feature has not been tested on a real iPhone or Windows PC.
- The PC's Windows login session must be active for opening desktop applications.
- To stop exposure: `tailscale serve reset`; stop `python remote/agent.py`. Rotate the token if disclosed.
