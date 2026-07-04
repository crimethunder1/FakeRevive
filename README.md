# FakeLeaveAndRevive

Paper-Plugin für Minecraft 1.21.4+. Wenn ein Spieler stirbt, wird die normale Todesnachricht durch eine gefälschte "hat das Spiel verlassen"-Nachricht ersetzt. Der Spieler landet danach im Spectator-Modus an seiner Todesposition, statt den regulären Respawn-Bildschirm zu sehen. Verlässt er in diesem Zustand tatsächlich den Server, wird auch dafür keine Quit-Nachricht angezeigt.

Mit `/revive` lässt sich ein so "fake-out"-gesetzter Spieler wieder zurück in den Survival-Modus holen. Dabei erhält er bis zu seinem nächsten Tod einen zufälligen Fake-Namen (Chat, Tab-Liste, Nametag) über [LibsDisguises](https://github.com/libraryaddict/LibsDisguises) — siehe "Fake-Namen bei Revive" unten.

## Befehl

```
/revive <Spieler>
/revive @a
```

Belebt entweder einen einzelnen oder alle aktuell fake-out-gesetzten Spieler wieder. Erfordert die Permission `fakeleaveandrevive.revive`.

## Fake-Namen bei Revive

Beim Revive bekommt der Spieler einen zufälligen, noch nie vergebenen Fake-Namen (Format `Adjektiv_Substantiv`, z. B. `Crimson_Wolf`) zugewiesen und wird darunter für alle anderen Spieler verkleidet. Stirbt er erneut, wird die Verkleidung entfernt und der Fake-Name dauerhaft aus dem Pool entfernt (nie erneute Vergabe). Die Verkleidung übersteht auch ein Verlassen und Wiederbetreten des Servers, solange der Spieler zwischenzeitlich nicht gestorben ist.

**Voraussetzungen auf dem Server:** Neben dieser Plugin-Jar müssen zusätzlich [LibsDisguises](https://www.spigotmc.org/resources/libs-disguises-free.81/) **und** [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) als eigene Jars im `plugins`-Ordner liegen. PacketEvents ist eine Abhängigkeit von LibsDisguises selbst (nicht von diesem Plugin) und wird daher nicht in `plugin.yml` als `depend` dieses Plugins geführt, muss aber trotzdem installiert sein, sonst startet LibsDisguises nicht.

**Bekannte Einschränkungen:**
- Der "Klick-Name" (Tab-Completion/Autovervollständigung von Spielernamen, z. B. bei `/msg <Tab>`) bleibt der echte Spielername — das ist eine Einschränkung der Minecraft-Serverarchitektur (Tab-Completion arbeitet mit den tatsächlich angemeldeten Spielernamen) und lässt sich über LibsDisguises nicht vollständig umgehen.
- Ist der Fake-Namen-Pool erschöpft (alle generierten Namen bereits vergeben), wird trotzdem ganz normal revived, nur ohne neue Verkleidung — dazu erscheint eine Warnung im Server-Log. Bei den mitgelieferten 500 generierten Namen sollte das in der Praxis nicht vorkommen.
- Das eigentliche Verkleidungsverhalten (Aussehen in Chat/Tab/Nametag) ist nicht automatisiert getestet, da MockBukkit LibsDisguises nicht simuliert — das ist ein manueller Smoke-Test auf einem echten Server (siehe unten).

Der mitgelieferte Namens-Pool (`src/main/resources/names.json`) wurde einmalig mit `com.deathrevive.plugin.tools.NameListGenerator` erzeugt: lokale Adjektiv+Nomen-Kombination, anschließend gegen `api.mojang.com` geprüft, dass keiner der Namen zu einem existierenden Minecraft-Account gehört. Das Tool ist kein Teil der Plugin-Laufzeit und muss nur erneut laufen, wenn der Pool erweitert werden soll.

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
