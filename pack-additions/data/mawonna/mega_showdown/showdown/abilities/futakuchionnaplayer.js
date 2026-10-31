({
  onStart(pokemon) {
    let activated = false;
    for (const target of pokemon.foes()) {
      if (!activated) {
        this.add("-ability", pokemon, "Futakuchi-onna (Player)", "boost");
        activated = true;
      }
      if (target.volatiles["substitute"]) {
        this.add("-immune", target);
      } else {
        this.boost({ atk: -1, spa: -1 }, target, pokemon, null, true);
      }
    }
  },
  flags: {},
  name: "Futakuchi-onna (Player)",
  rating: 4,
  num: -9305
})
