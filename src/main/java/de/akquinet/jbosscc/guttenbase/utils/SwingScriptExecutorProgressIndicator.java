package de.akquinet.jbosscc.guttenbase.utils;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;

/**
 * Swing UI for script executor
 * <p>
 * &copy; 2013-2020 akquinet tech@spree
 * </p>
 * 
 * @author M. Dahm
 */
public class SwingScriptExecutorProgressIndicator implements ScriptExecutorProgressIndicator
{
  private final ScriptExecutorProgressIndicatorPanel _panel = new ScriptExecutorProgressIndicatorPanel();
  private final JDialog _dialog = new JDialog();
  private final TimingProgressIndicator _timingDelegate = new TimingProgressIndicator();
  private final StringBuilder _text = new StringBuilder();
  private TimerDaemonThread _timerDaemonThread;

  public SwingScriptExecutorProgressIndicator()
  {
    _dialog.setModal(true);
    _dialog.setTitle("Copying tables...");

    _dialog.addWindowListener(new WindowAdapter()
    {
      @Override
      public void windowClosed(final WindowEvent e)
      {
        finalizeIndicator();
      }
    });

    final Dimension size = new Dimension(800, 400);
    _dialog.getContentPane().setLayout(new BorderLayout());
    _dialog.getContentPane().add(_panel, BorderLayout.CENTER);

    _dialog.setSize(size);
    _dialog.setMinimumSize(size);
    _panel.setPreferredSize(size);
  }

  @Override
  public void initializeIndicator()
  {
    _timingDelegate.initializeIndicator();

    _panel.getTotalTime().setText("");
    _panel.getScriptTime().setText("");

    _timerDaemonThread = new TimerDaemonThread(_dialog, _timingDelegate, this);
    _timerDaemonThread.start();
  }

  @Override
  public void startProcess(final int numberOfItems)
  {
    _timingDelegate.startProcess(numberOfItems);

    _panel.getTotalProgress().setValue(0);
    _panel.getTotalProgress().setMinimum(0);
    _panel.getTotalProgress().setMaximum(numberOfItems);
  }

  @Override
  public void startExecution()
  {
    _timingDelegate.startExecution();
  }

  @Override
  public void endExecution(final int numberOfItems)
  {
    _timingDelegate.endExecution(numberOfItems);

    _panel.getTotalProgress().setValue(_timingDelegate.getItemCounter());
    updateTimers();
  }

  @Override
  public void endProcess()
  {
    _timingDelegate.endProcess();

    _panel.getTotalProgress().setValue(_timingDelegate.getItemCounter());
  }

  @Override
  public void warn(final String text)
  {
    _timingDelegate.warn(text);
    _text.append("WARNING: " + text + "\n");
    updateMessages();
  }

  @Override
  public void info(final String text)
  {
    _timingDelegate.info(text);
    _text.append("Info: " + text + "\n");
    updateMessages();
  }

  @Override
  public void debug(final String text)
  {
    _timingDelegate.debug(text);
    _text.append("Debug: " + text + "\n");
    updateMessages();
  }

  @Override
  public void finalizeIndicator()
  {
    _timingDelegate.finalizeIndicator();
    _timerDaemonThread.setActive(false);
    _dialog.setVisible(false);
    _dialog.dispose();
  }

  @Override
  public final void updateTimers()
  {
    _panel.getTotalTime().setText(Util.formatTime(_timingDelegate.getElapsedTotalTime()));
    _panel.getScriptTime().setText(Util.formatTime(_timingDelegate.getElapsedTableCopyTime()));
  }

  private void updateMessages()
  {
    _panel.getMessages().setText(_text.toString());
  }
}
