const readline = require('readline');

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

console.log("HELLO World!");

rl.question("Please, enter your name: ", function(name) {

  console.log(`Hello, ${name}!`);

  const lettersCount = name.length;

  // Функція для обчислення факторіалу
  function factorial(n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
  }

  const fact = factorial(lettersCount);

  console.log(`Your name has ${lettersCount} letters. ${lettersCount}! = ${fact}`);

  rl.question("Please, enter your birth date in format (DD.MM.YYYY): ", function(dateInput) {

    const [day, month, year] = dateInput.split('.').map(Number);
    const birthDate = new Date(year, month - 1, day);

    const today = new Date();

    // Обчислення повних років
    let ageYears = today.getFullYear() - birthDate.getFullYear();
    const monthDiff = today.getMonth() - birthDate.getMonth();
    const dayDiff = today.getDate() - birthDate.getDate();

    if (monthDiff < 0 || (monthDiff === 0 && dayDiff < 0)) {
      ageYears--;
    }

    // Обчислення різниці в днях
    const diffTime = today - birthDate;
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

    // Форматування сьогоднішньої дати
    const formattedToday = today.toLocaleDateString('uk-UA');

    console.log(`Today is ${formattedToday}, you are ${ageYears} year (${diffDays} days) old.`);

    rl.close();
  });
});
