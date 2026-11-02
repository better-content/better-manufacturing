package com.bettercontent.manufacturing.integration.sifter;

/** Implemented only by the pinned native Sifter hook; does not introduce a second mesh inventory. */
public interface ScreenAccess {
    MeshEngineering.State manufacturing$screenState();
    void manufacturing$meshChanged();
    void manufacturing$refreshScreen();
}
