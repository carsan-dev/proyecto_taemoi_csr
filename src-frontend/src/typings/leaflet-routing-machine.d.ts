// Asegúrate de que este archivo está ubicado en un directorio incluido en tu tsconfig.json
import * as L from 'leaflet';

declare module 'leaflet' {
  namespace Routing {
    function control(options?: Routing.ControlOptions): Routing.Control;

    class Control extends L.Control {
      constructor(options?: Routing.ControlOptions);
    }

    interface ControlOptions {
      waypoints?: L.LatLng[];
      routeWhileDragging?: boolean;
      router?: Routing.IRouter;
      plan?: Routing.IPlan;
      geocoder?: any;
      autoRoute?: boolean;
      routeLine?: (route: IRoute, options?: LineOptions) => L.LayerGroup;
      createMarker?: (
        i: number,
        waypoint: Waypoint,
        nWaypoints: number
      ) => L.Marker;
      formatter?: Routing.Formatter;
      summaryTemplate?: string;
      distanceTemplate?: string;
      timeTemplate?: string;
      containerClassName?: string;
      alternativeClassName?: string;
      minimizedClassName?: string;
      show?: boolean;
      collapsible?: boolean;
      collapsed?: boolean;
      showAlternatives?: boolean;
      altLineOptions?: LineOptions;
      lineOptions?: LineOptions;
      language?: string;
      // Agrega más opciones según tus necesidades
    }

    interface LineOptions extends L.PolylineOptions {
      addWaypoints?: boolean;
      extendToWaypoints?: boolean;
      missingRouteTolerance?: number;
      styles?: L.PathOptions[];
    }

    class Formatter {
      // Define métodos y propiedades si es necesario
    }

    interface IRoute {
      // Define las propiedades que necesites
    }

    interface Waypoint {
      latLng: L.LatLng;
      name?: string;
      options?: any;
    }

    interface IRouter {
      // Define métodos y propiedades si es necesario
    }

    interface IPlan {
      // Define métodos y propiedades si es necesario
    }
  }
}
