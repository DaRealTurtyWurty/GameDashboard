package domains.brighton.rg764.gamedashboard.data;

public enum GameService {
    STEAM("Steam"),
    ORIGIN("Origin"),
    EPIC_GAMES("Epic Games"),
    UPLAY("Uplay"),
    BATTLE_NET("Battle.net"),
    OTHER("Other");

    private final String name;

    GameService(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
