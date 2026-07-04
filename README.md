# FakeLeaveAndRevive

Paper-Plugin für Minecraft 1.21.4+. Wenn ein Spieler stirbt, wird die normale Todesnachricht durch eine gefälschte "hat das Spiel verlassen"-Nachricht ersetzt. Der Spieler landet danach im Spectator-Modus an seiner Todesposition, statt den regulären Respawn-Bildschirm zu sehen. Verlässt er in diesem Zustand tatsächlich den Server, wird auch dafür keine Quit-Nachricht angezeigt.

Mit `/revive` lässt sich ein so "fake-out"-gesetzter Spieler wieder zurück in den Survival-Modus holen.

## Befehl

```
/revive <Spieler>
/revive @a
```

Belebt entweder einen einzelnen oder alle aktuell fake-out-gesetzten Spieler wieder. Erfordert die Permission `fakeleaveandrevive.revive`.

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
