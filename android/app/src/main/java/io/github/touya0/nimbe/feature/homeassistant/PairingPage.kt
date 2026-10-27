package io.github.touya0.nimbe.feature.homeassistant

/**
 * Page web servie par la TV au téléphone qui a scanné le QR code. Autonome (pas de fichier externe),
 * aux couleurs de Nimbe. Le token n'est jamais réaffiché : en cas d'erreur, il faut le recoller.
 */
object PairingPage {

    /** [error] : message en rouge au-dessus du formulaire ; [address] et [code] pré-remplis. */
    fun form(address: String, code: String = "", error: String? = null): String = page(
        """
        <h1>Connecter Home Assistant</h1>
        <p class="muted">Nimbe affiche sur la TV l'état du Pi et des services. Le token reste chiffré sur la TV.</p>
        ${error?.let { """<p class="error">${escape(it)}</p>""" }.orEmpty()}
        <form method="post" action="/">
          <label>Code affiché sur la TV
            <input name="code" value="${escape(code)}" inputmode="numeric" autocomplete="one-time-code" maxlength="7" required placeholder="482 913">
          </label>
          <label>Adresse de Home Assistant
            <input name="address" value="${escape(address)}" inputmode="url" autocapitalize="none" autocorrect="off" spellcheck="false" required placeholder="http://192.168.1.20:8123">
          </label>
          <label>Token d'accès longue durée
            <textarea name="token" rows="5" autocapitalize="none" autocorrect="off" spellcheck="false" autocomplete="off" required></textarea>
          </label>
          <button type="submit">Envoyer à la TV</button>
        </form>
        <details>
          <summary>Où trouver le token ?</summary>
          <ol>
            <li>Dans Home Assistant, connecte-toi avec le compte de la TV (par exemple « tv-salon », sans droits admin).</li>
            <li>Ouvre ton profil, onglet Sécurité.</li>
            <li>En bas : Jetons d'accès longue durée → Créer un jeton, nomme-le « Nimbe salon ».</li>
            <li>Copie le jeton (il n'est montré qu'une fois) et colle-le ici.</li>
          </ol>
        </details>
        """,
    )

    fun accepted(userName: String): String = page(
        """
        <h1>Token reçu</h1>
        <p>Compte « ${escape(userName)} ». Continue sur la TV pour choisir les capteurs à afficher.</p>
        <p class="muted">Tu peux fermer cette page.</p>
        """,
    )

    fun notFound(): String = page("<h1>Page introuvable</h1>")

    private fun page(content: String): String =
        """
        <!doctype html>
        <html lang="fr">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex">
        <title>Nimbe · Home Assistant</title>
        <style>
          :root { color-scheme: dark; }
          body { margin: 0; padding: 24px 16px; background: #0B0E13; color: #F4F2EE;
                 font: 16px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
          main { max-width: 480px; margin: 0 auto; }
          h1 { font-size: 24px; margin: 0 0 8px; }
          .muted { color: rgba(244, 242, 238, .7); }
          .error { background: rgba(248, 113, 113, .15); color: #F87171; padding: 12px; border-radius: 10px; }
          form { display: grid; gap: 16px; margin: 20px 0; }
          label { display: grid; gap: 6px; font-weight: 600; }
          input, textarea { font: inherit; font-weight: 400; color: #F4F2EE; background: #141922;
                            border: 1px solid #232A36; border-radius: 10px; padding: 12px; }
          textarea { font-family: ui-monospace, monospace; font-size: 13px; word-break: break-all; }
          input:focus, textarea:focus { outline: 3px solid #F0B44C; outline-offset: 2px; }
          button { font: inherit; font-weight: 700; background: #F0B44C; color: #0B0E13; border: 0;
                   border-radius: 999px; padding: 14px; }
          details { background: #141922; border-radius: 10px; padding: 12px 16px; }
          summary { font-weight: 600; }
        </style>
        </head>
        <body><main>
        $content
        </main></body>
        </html>
        """.trimIndent()

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
