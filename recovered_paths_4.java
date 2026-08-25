<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Ancient Angler Guide</title>
    <style>
        :root {
            --deep-blue: #0b1d3a;
            --ocean-blue: #14365d;
            --gold: #d4af37;
            --light-gold: #f3e5ab;
            --text-light: #e0e6ed;
            --accent-cyan: #00e5ff;
        }

        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background-color: var(--deep-blue);
            color: var(--text-light);
            margin: 0;
            padding: 0;
            line-height: 1.6;
        }

        header {
            background: linear-gradient(135deg, var(--deep-blue) 0%, var(--ocean-blue) 100%);
            border-bottom: 3px solid var(--gold);
            padding: 2rem;
            text-align: center;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
        }

        h1 {
            color: var(--gold);
            margin: 0;
            font-size: 2.5rem;
            text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.5);
        }

        h1 span {
            color: var(--accent-cyan);
        }

        .container {
            max-width: 800px;
            margin: 2rem auto;
            padding: 0 1.5rem;
        }

        .section {
            background-color: rgba(20, 54, 93, 0.6);
            border-left: 4px solid var(--gold);
            border-radius: 8px;
            padding: 1.5rem;
            margin-bottom: 2rem;
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
            transition: transform 0.2s ease-in-out;
        }

        .section:hover {
            transform: translateY(-2px);
        }

        .section h2 {
            color: var(--light-gold);
            margin-top: 0;
            display: flex;
            align-items: center;
            gap: 10px;
            border-bottom: 1px solid rgba(212, 175, 55, 0.3);
            padding-bottom: 0.5rem;
        }

        .section p {
            margin-bottom: 0;
        }

        ul {
            list-style-type: none;
            padding-left: 0;
        }

        ul li {
            padding: 0.5rem 0;
            position: relative;
            padding-left: 1.5rem;
        }

        ul li::before {
            content: "✦";
            color: var(--gold);
            position: absolute;
            left: 0;
        }

        .highlight {
            color: var(--accent-cyan);
            font-weight: bold;
        }

        .warning {
            color: #ff6b6b;
            font-weight: bold;
        }
    </style>
</head>
<body>
    <header>
        <h1>🔱 The <span>Ancient Angler</span> Guide 🔱</h1>
        <p>A comprehensive wiki to mastering the mysteries of the deep.</p>
    </header>

    <div class="container">
        <!-- Who is the Ancient Angler -->
        <div class="section">
            <h2>👴 Who is the Ancient Angler?</h2>
            <p>
                The Ancient Angler is a mysterious entity of the deep sea. He isn't someone you just stumble upon—he must be summoned using the legendary <span class="highlight">Tidecaller's Pearl</span> compass. Once summoned, he won't just hand over his treasures; he will test your wits with complex <span class="highlight">riddles</span> and assign challenging <span class="highlight">tasks</span> to prove your worth as a master of the seas.
            </p>
        </div>

        <!-- How to get the compass -->
        <div class="section">
            <h2>🧭 How to Obtain the Compass</h2>
            <p>
                The <span class="highlight">Tidecaller's Pearl</span> is a highly coveted artifact, and there's only one way to get it: <span class="highlight">Fishing!</span> 🎣<br><br>
                While it is exceptionally rare, you don't have to rely purely on luck. The gods of the sea are merciful and have implemented a <strong>pity system</strong>. If you spend enough time casting your line into the waters, you are <em>guaranteed</em> to fish it up eventually. Patience is key!
            </p>
        </div>

        <!-- What happens if wrong -->
        <div class="section">
            <h2>⚠️ The Price of Failure</h2>
            <p>
                The Ancient Angler does not tolerate fools. If you answer his riddles incorrectly or fail his tasks, you will face severe consequences. The fail mechanics include:
            </p>
            <ul>
                <li><span class="warning">Nausea:</span> The sea will turn your stomach, disorienting your vision. 🤢</li>
                <li><span class="warning">Blindness:</span> The darkness of the ocean depths will temporarily cloud your sight. 👁️‍🗨️</li>
                <li><span class="warning">Lightning Strikes:</span> The fury of the storm will be called down upon you. ⚡</li>
            </ul>
        </div>

        <!-- The Paths -->
        <div class="section">
            <h2>🛤️ Dialogue Paths & Choices</h2>
            <p>
                Your journey with the Ancient Angler is not linear. There are <span class="highlight">multiple paths</span> you can take, heavily influenced by the dialogue choices you make during your encounters. Whether you choose a path of wisdom, arrogance, or humility, your interactions will shape the tasks he gives you and the ultimate rewards you can reap. Choose your words carefully! 🗣️
            </p>
        </div>
    </div>
</body>
</html>