package com.cyr1en.cp.prompt;

public interface Prompt {

  /**
   * Get the identifier for the prompt.
   *
   * An identifier is a {@link String} that signifies the type of a prompt.
   *
   * @return Returns the identifier for the prompt.
   */
  String getTrigger();

  /**
   * Method that sends the prompt.
   */
  void sendPrompt();

  /**
   * Process the input.
   *
   * This goes after the user reply to the prompt that have been sent
   * through
   */
  void process();


  void onPrompt(String arguments);

  boolean isOP();
}
