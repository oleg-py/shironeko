if (process.env.NODE_ENV === "production") {
    const opt = require("./shironeko-slinky-todomvc-opt.js");
    opt.main();
    module.exports = opt;
} else {
    var exports = window;
    exports.require = require("./shironeko-slinky-todomvc-fastopt-entrypoint.js").require;
    window.global = window;

    if (!window.process) {
        // FIXME use cats-effect once released 2.1.3 has a bug on IOApp in browser
        window.process = { on: function() { } }
    }

    const fastOpt = require("./shironeko-slinky-todomvc-fastopt.js");
    fastOpt.main()
    module.exports = fastOpt;

    if (module.hot) {
        module.hot.accept();
    }
}
