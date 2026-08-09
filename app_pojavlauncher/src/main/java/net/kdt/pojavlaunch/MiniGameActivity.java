package net.kdt.pojavlaunch;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class MiniGameActivity extends AppCompatActivity {

    private TextView scoreText;
    private TextView targetText;
    private TextView statusText;
    private TextView highScoreText;

    private Button actionButton;
    private Button backButton;

    private final Random random = new Random();
    private SharedPreferences prefs;

    private int score;
    private int lives;
    private int combo;
    private int round;
    private int target;
    private int gameType;

    private boolean gameOver;
    private boolean hazardRound;
    private boolean powerUpRound;
    private boolean doubleScore;
    private boolean shieldActive;
    private int powerUpType;

    private CountDownTimer timer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable nextRoundRunnable = () -> {
        if (!isFinishing() && !isDestroyed() && !gameOver) {
            nextRound();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minigame);

        scoreText = findViewById(R.id.minigame_score);
        targetText = findViewById(R.id.minigame_target);
        statusText = findViewById(R.id.minigame_status);
        highScoreText = findViewById(R.id.minigame_highscore);
        actionButton = findViewById(R.id.minigame_action);
        backButton = findViewById(R.id.minigame_back);

        prefs = getSharedPreferences("minigame", MODE_PRIVATE);

        backButton.setOnClickListener(v -> finish());
        actionButton.setOnClickListener(v -> handleAction());

        updateHighScore();
        startGame();
    }

    private void startGame() {
        cancelTimer();

        score = 0;
        lives = 3;
        combo = 0;
        round = 0;
        gameOver = false;
        hazardRound = false;
        powerUpRound = false;
        doubleScore = false;
        shieldActive = false;

        actionButton.setEnabled(true);
        actionButton.setText("GO!");

        updateScore();
        statusText.setText("Get ready...");

        nextRound();
    }

    private void nextRound() {
        if (gameOver) {
            return;
        }

        cancelTimer();

        round++;
        hazardRound = false;
        powerUpRound = false;

        int difficulty = Math.min(10, 1 + round / 5);

        // Special rounds are uncommon so the normal game stays quick.
        int special = random.nextInt(10);

        if (round >= 3 && special == 0) {
            powerUpRound = true;
            powerUpType = random.nextInt(2);

            if (powerUpType == 0) {
                targetText.setText("SHIELD");
                actionButton.setText("TAKE SHIELD");
                statusText.setText("Grab it! Blocks your next mistake.");
            } else {
                targetText.setText("2X SCORE");
                actionButton.setText("TAKE BOOST");
                statusText.setText("Grab it! Doubles your next score.");
            }

            startTimer(3500);
            return;
        }

        // Hazards are intentionally rare.
        hazardRound = random.nextInt(8) == 0;

        if (hazardRound) {
            targetText.setText("HAZARD!");
            actionButton.setText("DON'T TOUCH!");
            statusText.setText("WAIT! Don't press the button!");

            startTimer(Math.max(1000, 2800 - round * 60L));
            return;
        }

        gameType = random.nextInt(3);

        if (gameType == 0) {
            target = 2 + random.nextInt(4 + difficulty);

            targetText.setText("SUGAR x" + target);
            actionButton.setText("COLLECT");
            statusText.setText("Collect the sugar!");

        } else if (gameType == 1) {
            target = 2 + random.nextInt(8);

            targetText.setText("SUGAR " + target);
            actionButton.setText("GRAB IT!");
            statusText.setText("Grab it quickly!");

        } else {
            target = 1 + random.nextInt(20);

            targetText.setText("SUGAR " + target);
            actionButton.setText(target % 2 == 0 ? "EVEN!" : "ODD!");
            statusText.setText("Pick the correct answer!");
        }

        actionButton.setEnabled(true);

        long time = Math.max(900, 3000 - round * 80L);
        startTimer(time);
    }

    private void startTimer(long duration) {
        timer = new CountDownTimer(duration, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!hazardRound) {
                    statusText.setText(
                            "TIME: " + ((millisUntilFinished + 99) / 1000)
                    );
                }
            }

            @Override
            public void onFinish() {
                if (gameOver) {
                    return;
                }

                if (hazardRound) {
                    // Successfully waited through the hazard.
                    statusText.setText("NICE! You avoided it!");
                    actionButton.setEnabled(false);

                    scheduleNextRound(250);
                } else {
                    loseLife();
                }
            }
        }.start();
    }

    private void handleAction() {
        if (gameOver || !actionButton.isEnabled()) {
            return;
        }

        if (hazardRound) {
            cancelTimer();
            statusText.setText("BOOM! You hit the hazard!");
            loseLife();
            return;
        }

        if (powerUpRound) {
            cancelTimer();

            if (powerUpType == 0) {
                shieldActive = true;
                statusText.setText("SHIELD READY!");
            } else {
                doubleScore = true;
                statusText.setText("2X SCORE READY!");
            }

            actionButton.setEnabled(false);

            scheduleNextRound(500);

            return;
        }

        boolean correct = true;

        // Reaction round: clicking too late counts as a miss.
        if (gameType == 1 && timer != null) {
            correct = timer.getMillisUntilFinished() > 1200;
        }

        cancelTimer();

        if (!correct) {
            loseLife();
            return;
        }

        combo++;

        int points = 10 + combo * 2;

        if (doubleScore) {
            points *= 2;
            doubleScore = false;
        }

        score += points;

        statusText.setText("NICE! +" + points);
        updateScore();

        actionButton.setEnabled(false);

        scheduleNextRound(250);
    }

    private void loseLife() {
        cancelTimer();

        if (shieldActive) {
            shieldActive = false;
            combo = 0;

            statusText.setText("SHIELD SAVED YOU!");
            updateScore();

            actionButton.setEnabled(false);

            scheduleNextRound(500);

            return;
        }

        lives--;
        combo = 0;

        if (lives <= 0) {
            endGame();
            return;
        }

        statusText.setText("MISS! Lives: " + lives);
        updateScore();

        actionButton.setEnabled(false);

        scheduleNextRound(500);
    }

    private void endGame() {
        cancelTimer();

        gameOver = true;

        targetText.setText("GAME OVER");
        statusText.setText("Final score: " + score);

        int highScore = prefs.getInt("high_score", 0);

        if (score > highScore) {
            prefs.edit().putInt("high_score", score).apply();
            highScoreText.setText("NEW HIGH SCORE!");
        } else {
            highScoreText.setText("High Score: " + highScore);
        }

        actionButton.setEnabled(true);
        actionButton.setText("PLAY AGAIN");
        actionButton.setOnClickListener(v -> {
            actionButton.setOnClickListener(v2 -> handleAction());
            startGame();
        });
    }

    private void updateScore() {
        scoreText.setText(
                "Score: " + score +
                "   ♥ " + lives +
                "   Combo: " + combo
        );
    }

    private void updateHighScore() {
        int highScore = prefs.getInt("high_score", 0);
        highScoreText.setText("High Score: " + highScore);
    }

    private void scheduleNextRound(long delay) {
        handler.removeCallbacks(nextRoundRunnable);
        handler.postDelayed(nextRoundRunnable, delay);
    }

    private void cancelPendingActions() {
        handler.removeCallbacks(nextRoundRunnable);
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    protected void onDestroy() {
        cancelTimer();
        cancelPendingActions();
        super.onDestroy();
    }

    public void closeGame(View view) {
        finish();
    }
}
