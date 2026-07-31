# FakeLeaveAndRevive

Paper-Plugin für Minecraft 1.21.4+. Wenn ein Spieler stirbt, wird die normale Todesnachricht durch eine gefälschte "hat das Spiel verlassen"-Nachricht ersetzt. Der Spieler landet danach im Spectator-Modus an seiner Todesposition, statt den regulären Respawn-Bildschirm zu sehen. Verlässt er in diesem Zustand tatsächlich den Server, wird auch dafür keine Quit-Nachricht angezeigt.

Mit `/revive` lässt sich ein so "fake-out"-gesetzter Spieler wieder zurück in den Survival-Modus holen. Dabei erhält er bis zu seinem nächsten Tod einen zufälligen Fake-Namen samt passendem Skin (Chat, Tab-Liste, Nametag, Aussehen) über [LibsDisguises](https://github.com/libraryaddict/LibsDisguises) — siehe "Fake-Namen bei Revive" unten.

## Befehl

```
/revive <Spieler>
/revive @a
```

Belebt entweder einen einzelnen oder alle aktuell fake-out-gesetzten Spieler wieder. Erfordert die Permission `fakeleaveandrevive.revive`.

## Fake-Namen bei Revive

Beim Revive bekommt der Spieler einen zufälligen, noch nie vergebenen Fake-Namen (verschiedene realistisch wirkende Handle-Stile, z. B. `ShadowHunter`, `Crimson_Wolf`, `Wolf123`) samt einem dazu fest zugeordneten Skin zugewiesen und wird darunter für alle anderen Spieler verkleidet. Stirbt er erneut, wird die Verkleidung entfernt und der Fake-Name (inkl. Skin) dauerhaft aus dem Pool entfernt (nie erneute Vergabe). Die Verkleidung übersteht auch ein Verlassen und Wiederbetreten des Servers, solange der Spieler zwischenzeitlich nicht gestorben ist.

**Voraussetzungen auf dem Server:** Neben dieser Plugin-Jar müssen zusätzlich [LibsDisguises](https://www.spigotmc.org/resources/libs-disguises-free.81/) **und** [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) als eigene Jars im `plugins`-Ordner liegen. PacketEvents ist eine Abhängigkeit von LibsDisguises selbst (nicht von diesem Plugin) und wird daher nicht in `plugin.yml` als `depend` dieses Plugins geführt, muss aber trotzdem installiert sein, sonst startet LibsDisguises nicht.

**Bekannte Einschränkungen:**
- Der "Klick-Name" (Tab-Completion/Autovervollständigung von Spielernamen, z. B. bei `/msg <Tab>`) bleibt der echte Spielername — das ist eine Einschränkung der Minecraft-Serverarchitektur (Tab-Completion arbeitet mit den tatsächlich angemeldeten Spielernamen) und lässt sich über LibsDisguises nicht vollständig umgehen.
- Ist der Fake-Namen-Pool im Moment eines Revives leer (z. B. direkt nach dem Serverstart bei sehr vielen Revives kurz hintereinander), wird trotzdem ganz normal revived, nur ohne neue Verkleidung — dazu erscheint eine Warnung im Server-Log. Der Pool füllt sich danach live wieder auf (siehe unten), sodass das nur ein kurzzeitiger Zustand ist.
- Das eigentliche Verkleidungsverhalten (Aussehen in Chat/Tab/Nametag) ist nicht automatisiert getestet, da MockBukkit LibsDisguises nicht simuliert — das ist ein manueller Smoke-Test auf einem echten Server (siehe unten).

Der mitgelieferte Namens-Pool (`src/main/resources/names.json`, aktuell ca. 200 Einträge) wurde mit `com.deathrevive.plugin.tools.NameListGenerator` erzeugt: verschiedene realistisch wirkende Handle-Stile (CamelCase- und Unterstrich-Kombinationen, Wort+Zahl, Präfix-/Suffix-Handles wie `TheDragon` oder `WolfYT` usw.), anschließend jeweils gegen `api.mojang.com` geprüft, dass keiner der Namen zu einem existierenden Minecraft-Account gehört. Jeder so verifizierte Fake-Name wird zusätzlich mit dem Skin eines echten, bereits vergebenen Accounts ("Skin-Donor") gepaart: Sobald der Generator beim Prüfen auf einen bereits vergebenen Namen stößt, holt er sich dessen aktuellen Skin (signierte Textur) über den Mojang-Session-Server und hinterlegt ihn fest zusammen mit dem Fake-Namen. Der angezeigte Name gehört also nie zum selben Account wie der angezeigte Skin. Jeder Eintrag in `names.json` hat die Form `{"name": "...", "skinValue": "...", "skinSignature": "..."}`. Das Tool ist kein Teil der Plugin-Laufzeit und muss nur erneut laufen, wenn der mitgelieferte Startpool von Grund auf neu befüllt werden soll.

**Live-Nachschub während des Betriebs:** Der mitgelieferte Pool ist bewusst klein (~200 statt tausender Einträge), da das Massen-Generieren im Voraus sehr stark von Mojangs Rate-Limiting ausgebremst wird. Stattdessen holt sich das Plugin selbst automatisch Nachschub: Jedes Mal, wenn `/revive` einen Namen+Skin aus dem Pool zieht, wird direkt danach im Hintergrund (`MojangIdentityFetcher`, asynchron, blockiert den Hauptthread nicht) ein neuer, frisch verifizierter Ersatz von der echten Mojang-API geholt und dem Pool hinzugefügt — der Pool bleibt so über die Zeit ungefähr konstant groß. Das bedeutet: der Server braucht dauerhaften Internetzugriff auf `api.mojang.com`/`sessionserver.mojang.com`, und einzelne Nachschub-Versuche können bei Mojang-Rate-Limiting fehlschlagen (dann bleibt der Pool einfach etwas kleiner, bis der nächste Revive es erneut versucht) — das eigentliche `/revive` selbst wartet darauf nicht und bleibt sofort nutzbar. Neu geholte Identitäten werden nur im Arbeitsspeicher gehalten und nicht in `names.json` zurückgeschrieben, gehen also bei einem Server-Neustart wieder verloren (der Pool startet dann wieder bei den ~200 mitgelieferten Einträgen).

## Build

Voraussetzungen: JDK 21, Maven.

```
mvn package
```

Das fertige Plugin liegt danach unter `target/fake-leave-and-revive-<version>.jar` und kann in den `plugins`-Ordner eines Paper-Servers (1.21.4+) kopiert werden.

Unter JDK 25 kann der Build (auch `mvn test`) mit `Cannot load from object array because "this.hashes" is null` fehlschlagen (javac-Bug, unabhängig vom Plugin-Code). Workaround: `-Dmaven.compiler.fork=true` anhängen, z.B. `mvn package -Dmaven.compiler.fork=true`.

## Tests

```
mvn test
```

Die Tests nutzen [MockBukkit](https://github.com/MockBukkit/MockBukkit) zum Simulieren von Server, Spielern und Events.
