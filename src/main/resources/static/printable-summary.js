document.addEventListener('DOMContentLoaded', function () {
  var printBtn = document.getElementById('btn-print-summary');
  if (printBtn) {
    printBtn.addEventListener('click', function () {
      window.print();
    });
  }
});

