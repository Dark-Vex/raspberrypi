package com.github.cecchisandrone.raspio.input;

import com.github.cecchisandrone.raspio.input.JoypadController.Analog;
import com.github.cecchisandrone.raspio.input.JoypadController.Button;

/**
 * Event generated by a {@link JoypadController}
 * 
 */
public class JoypadEvent {

  private int newValue;

  private int oldValue;

  private Button changedButton;

  private Analog changedAnalog;

  public JoypadEvent(int newValue, int oldValue, Button changedButton) {
    this.newValue = newValue;
    this.oldValue = oldValue;
    this.changedButton = changedButton;
  }

  public JoypadEvent(int newValue, int oldValue, Analog changedAnalog) {
    this.newValue = newValue;
    this.oldValue = oldValue;
    this.changedAnalog = changedAnalog;
  }

  public int getNewValue() {
    return newValue;
  }

  public int getOldValue() {
    return oldValue;
  }

  public Button getChangedButton() {
    return changedButton;
  }

  public Analog getChangedAnalog() {
    return changedAnalog;
  }
}
