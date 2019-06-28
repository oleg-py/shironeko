if (process.env.NODE_ENV === "production") {
    const opt = require("./shironeko-slinky-todomvc-opt.js");
    opt.main();
    module.exports = opt;
} else {
    var exports = window;
    exports.require = require("./shironeko-slinky-todomvc-fastopt-entrypoint.js").require;
    window.global = window;

    const fastOpt = require("./shironeko-slinky-todomvc-fastopt.js");
    fastOpt.main()
    module.exports = fastOpt;

    if (module.hot) {
        module.hot.accept();
    }
}
